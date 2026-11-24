package dev.saq.portfolio.service;

import java.util.List;

import dev.saq.portfolio.config.AiModelConfig;
import dev.saq.portfolio.config.ChatProperties;
import dev.saq.portfolio.dto.event.DoneEvent;
import dev.saq.portfolio.dto.event.SourcesEvent;
import dev.saq.portfolio.dto.event.TokenEvent;
import dev.saq.portfolio.dto.internal.RetrievedContext;
import dev.saq.portfolio.dto.request.ChatRequest;
import dev.saq.portfolio.dto.response.ChatSourceResponse;
import dev.saq.portfolio.exception.ProblemTypes;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.model.ModelTier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;

/**
 * Answers one question as a stream of server-sent events, from whichever model answers first.
 *
 * <h2>Why the first token is resolved before the method returns</h2>
 *
 * <p>The contract promises two things that pull against each other: a 503 Problem when neither
 * model can answer, and a token-by-token stream when one can. Once an SSE response is committed
 * the status line is gone — a failure after that point can only be reported inside the stream,
 * which no HTTP client treats as an error. So this method blocks until the chosen model has
 * actually produced its first token, and only then hands back a {@link Flux}. Before that point
 * failures are real status codes; after it, they are {@code error} events.
 *
 * <p>Blocking is affordable here and nowhere near as expensive as it looks: Spring MVC runs on
 * virtual threads ({@code spring.threads.virtual.enabled}), so a request waiting on a model is a
 * parked continuation rather than a pinned platform thread.
 *
 * <h2>Why the fallback is chosen on the first token rather than on completion</h2>
 *
 * <p>The failures worth surviving are the ones free tiers actually produce: a 429, a cold start
 * that never responds, an expired key. All of them happen before any output. Switching on the
 * <em>first token</em> catches every one of them while the response is still uncommitted, and
 * avoids the alternative of buffering a whole answer to find out whether it arrived — which
 * would throw away streaming, the one thing that makes a grounded answer feel fast.
 *
 * <p>After the first token the tier is settled. A model that dies halfway through cannot be
 * replaced by another: the visitor has already read half an answer, and a second model would
 * start its own from the beginning. That case ends the stream with an {@code error} event.
 */
@Service
public class ChatService {

	/** SSE event names, as published in the contract's description of this endpoint. */
	private static final String TOKEN_EVENT = "token";

	private static final String SOURCES_EVENT = "sources";

	private static final String DONE_EVENT = "done";

	private static final String ERROR_EVENT = "error";

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private final ChatClient primaryClient;

	private final ChatClient fallbackClient;

	private final RetrievalService retrievalService;

	private final PromptService promptService;

	private final ProblemTypes problemTypes;

	private final ChatProperties properties;

	public ChatService(
			@Qualifier(AiModelConfig.PRIMARY_CHAT_CLIENT) ChatClient primaryClient,
			@Qualifier(AiModelConfig.FALLBACK_CHAT_CLIENT) ChatClient fallbackClient,
			RetrievalService retrievalService, PromptService promptService, ProblemTypes problemTypes,
			ChatProperties properties) {

		this.primaryClient = primaryClient;
		this.fallbackClient = fallbackClient;
		this.retrievalService = retrievalService;
		this.promptService = promptService;
		this.problemTypes = problemTypes;
		this.properties = properties;
	}

	/**
	 * Retrieves, prompts, and streams the answer.
	 *
	 * @param request a validated conversation whose last message is the visitor's question
	 * @return {@code token}* then {@code sources} then {@code done}, or a single {@code error}
	 *         event if the model fails after the stream has begun
	 * @throws UpstreamUnavailableException when neither model produces a first token, which the
	 *                                      handler turns into the contract's 503 — thrown before
	 *                                      anything is written, so the status code still applies
	 */
	public Flux<ServerSentEvent<Object>> stream(ChatRequest request) {
		RetrievedContext retrieved = retrievalService.retrieve(request.question());
		Prompt prompt = promptService.build(request, retrieved);

		Attempt attempt = firstTokenFrom(ModelTier.PRIMARY, primaryClient, prompt);
		if (attempt == null) {
			attempt = firstTokenFrom(ModelTier.FALLBACK, fallbackClient, prompt);
		}
		if (attempt == null) {
			throw new UpstreamUnavailableException(
					"The assistant is unavailable: no language model answered.");
		}

		log.debug("Answering from the {} model with {} source(s)", attempt.tier(),
				retrieved.sources().size());
		return events(attempt, retrieved.sources());
	}

	/**
	 * Starts one model and waits for its first token.
	 *
	 * <p>The stream is turned into a replaying {@link ConnectableFlux} and connected before
	 * anything is read, so the token consumed here is not lost: the SSE subscriber that arrives
	 * later replays it along with everything buffered since. Without that, peeking at the first
	 * token to decide the tier would mean dropping the first word of every answer.
	 *
	 * @return the attempt, or {@code null} if this model failed, timed out or said nothing — all
	 *         three being the same thing to the caller, which is "try the other one"
	 */
	private Attempt firstTokenFrom(ModelTier tier, ChatClient client, Prompt prompt) {
		ConnectableFlux<String> tokens;
		Disposable connection;
		try {
			tokens = client.prompt(prompt)
					.stream()
					.content()
					// Providers emit empty chunks around tool and role boundaries. An empty first
					// token would otherwise count as an answer and commit the response to a model
					// that has not said anything yet.
					.filter(token -> !token.isEmpty())
					.replay();
			connection = tokens.connect();
		}
		catch (RuntimeException ex) {
			// A misconfigured client (bad base URL, absent key) throws on subscribe rather than
			// signalling an error, so the synchronous path needs catching too.
			log.warn("The {} model could not be called: {}", tier, ex.getMessage());
			return null;
		}

		try {
			String first = tokens.next().block(properties.firstTokenTimeout());
			if (first == null) {
				// Completed without emitting: a content filter or a safety block, which is a
				// failure to answer even though nothing went wrong technically.
				log.warn("The {} model completed without producing any text", tier);
				connection.dispose();
				return null;
			}
			return new Attempt(tier, tokens, connection);
		}
		catch (RuntimeException ex) {
			log.warn("The {} model produced no first token within {}: {}", tier,
					properties.firstTokenTimeout(), ex.getMessage());
			// Disposed rather than abandoned: an unread connection would keep consuming the
			// provider's quota for an answer nobody will ever see.
			connection.dispose();
			return null;
		}
	}

	/**
	 * The event stream: every token, then the citations, then which model answered.
	 *
	 * <p>Citations last because they are only trustworthy once the answer they belong to exists,
	 * and because a visitor reading an answer should not be handed links to read first.
	 */
	private Flux<ServerSentEvent<Object>> events(Attempt attempt, List<ChatSourceResponse> sources) {
		return attempt.tokens()
				.map(text -> event(TOKEN_EVENT, new TokenEvent(text)))
				.concatWith(Flux.just(event(SOURCES_EVENT, new SourcesEvent(sources))))
				.concatWith(Flux.just(event(DONE_EVENT, DoneEvent.of(attempt.tier()))))
				// Reached only once tokens are already on the wire, so there is no status code
				// left to set. The stream reports the failure and ends cleanly; `done` never
				// arrives, which is how the client knows the answer is incomplete.
				.onErrorResume(ex -> {
					log.warn("The {} model failed after streaming had begun", attempt.tier(), ex);
					return Flux.just(event(ERROR_EVENT, midStreamProblem()));
				})
				// Covers both the happy path and a visitor closing the panel mid-answer.
				.doFinally(signal -> attempt.connection().dispose());
	}

	/**
	 * The {@code error} event's payload: the same RFC 9457 shape the rest of the API returns.
	 *
	 * <p>503 rather than 500 because that is what happened — an upstream stopped answering — and
	 * because it tells the client the request is worth retrying. Carried inside the stream, so it
	 * is a description of a failure rather than the response's status.
	 */
	private ProblemDetail midStreamProblem() {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
		problem.setType(problemTypes.upstreamUnavailable());
		problem.setTitle("Service unavailable");
		problem.setDetail("The answer was cut short because the assistant stopped responding.");
		return problem;
	}

	private static ServerSentEvent<Object> event(String name, Object data) {
		return ServerSentEvent.builder().event(name).data(data).build();
	}

	/**
	 * A model that has committed to answering: which one it is, its replaying token stream, and
	 * the handle that stops it.
	 */
	private record Attempt(ModelTier tier, Flux<String> tokens, Disposable connection) {
	}

}
