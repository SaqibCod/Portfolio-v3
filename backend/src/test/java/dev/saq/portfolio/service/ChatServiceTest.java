package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import dev.saq.portfolio.config.ChatProperties;
import dev.saq.portfolio.dto.event.DoneEvent;
import dev.saq.portfolio.dto.event.SourcesEvent;
import dev.saq.portfolio.dto.event.TokenEvent;
import dev.saq.portfolio.dto.internal.RetrievedContext;
import dev.saq.portfolio.dto.request.ChatMessageRequest;
import dev.saq.portfolio.dto.request.ChatRequest;
import dev.saq.portfolio.dto.response.ChatSourceResponse;
import dev.saq.portfolio.exception.ProblemTypes;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.model.ChatRole;
import dev.saq.testsupport.StubChatModel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;

/**
 * The streaming contract and the tier switch, which together are the whole of this service.
 *
 * <p>Models are {@link StubChatModel}s behind real {@code ChatClient}s, so the Spring AI call
 * path is exercised while nothing leaves the JVM. Retrieval and prompting are mocked: what is
 * under test here is the order of events on the wire and which model ends up answering, and both
 * are independent of what was retrieved.
 */
class ChatServiceTest {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	/** Short enough that the "primary never answers" case does not slow the suite down. */
	private static final Duration FIRST_TOKEN_TIMEOUT = Duration.ofMillis(200);

	private static final ChatSourceResponse ABOUT_SOURCE =
			new ChatSourceResponse("About Saq", "/#about", "Saq is a software engineer.");

	private RetrievalService retrievalService;

	private PromptService promptService;

	private ProblemTypes problemTypes;

	@BeforeEach
	void setUp() {
		retrievalService = mock(RetrievalService.class);
		promptService = mock(PromptService.class);
		// Mocked rather than built from AppProperties: the type URI is the only thing this
		// service asks it for, and ProblemTypesTest already pins how the URIs are composed.
		problemTypes = mock(ProblemTypes.class);
		given(problemTypes.upstreamUnavailable())
				.willReturn(URI.create(PROBLEM_BASE + "upstream-unavailable"));

		given(retrievalService.retrieve(any()))
				.willReturn(new RetrievedContext(List.of("a chunk"), List.of(ABOUT_SOURCE)));
		given(promptService.build(any(), any()))
				.willReturn(new Prompt(List.of(new UserMessage("What does Saq build?"))));
	}

	@Test
	void streamsTokensThenSourcesThenDone() {
		List<ServerSentEvent<Object>> events = events(
				StubChatModel.answering("Saq ", "builds ", "things."), StubChatModel.silent());

		// The contract's order, and the reason for it: citations are only meaningful once the
		// answer they belong to exists, and `done` is how the client tells a finished answer
		// from a dropped connection.
		assertThat(events).extracting(ServerSentEvent::event)
				.containsExactly("token", "token", "token", "sources", "done");
	}

	@Test
	void doesNotSwallowTheFirstToken() {
		List<ServerSentEvent<Object>> events = events(
				StubChatModel.answering("Saq ", "builds ", "things."), StubChatModel.silent());

		// The first token is consumed before the response is committed, to decide the tier. If it
		// were not replayed to the SSE subscriber, every answer would silently lose its first
		// word -- which reads as a model quirk rather than a bug, so it is worth pinning.
		assertThat(answerText(events)).isEqualTo("Saq builds things.");
	}

	@Test
	void reportsThePrimaryModelInTheDoneEvent() {
		List<ServerSentEvent<Object>> events = events(
				StubChatModel.answering("Answer."), StubChatModel.silent());

		assertThat(doneModel(events)).isEqualTo("primary");
	}

	@Test
	void attachesTheRetrievedSourcesToTheAnswer() {
		List<ServerSentEvent<Object>> events = events(
				StubChatModel.answering("Answer."), StubChatModel.silent());

		SourcesEvent sources = (SourcesEvent) dataOf(events, "sources");
		assertThat(sources.sources()).containsExactly(ABOUT_SOURCE);
	}

	@Test
	void sendsAnEmptySourcesEventWhenNothingWasRetrieved() {
		given(retrievalService.retrieve(any())).willReturn(RetrievedContext.empty());

		List<ServerSentEvent<Object>> events = events(
				StubChatModel.answering("I don't have that on the site."), StubChatModel.silent());

		// The event is still sent, empty. A client that waited for `sources` before rendering
		// would otherwise hang on exactly the answers that need no citation.
		SourcesEvent sources = (SourcesEvent) dataOf(events, "sources");
		assertThat(sources.sources()).isEmpty();
	}

	@Test
	void fallsBackWhenThePrimaryFailsBeforeTheFirstToken() {
		StubChatModel fallback = StubChatModel.answering("Fallback ", "answer.");

		List<ServerSentEvent<Object>> events = events(StubChatModel.failingBeforeFirstToken(), fallback);

		// A 429 or an expired key, which is what a free tier actually does. Nothing has been
		// written yet, so the switch is invisible to the visitor apart from `done.model`.
		assertThat(answerText(events)).isEqualTo("Fallback answer.");
		assertThat(doneModel(events)).isEqualTo("fallback");
		assertThat(fallback.calls()).isEqualTo(1);
	}

	@Test
	void fallsBackWhenThePrimaryNeverProducesAToken() {
		StubChatModel fallback = StubChatModel.answering("Fallback.");

		List<ServerSentEvent<Object>> events = events(StubChatModel.hanging(), fallback);

		// A cold start that never answers. The timeout, not an error, is what gives up -- and it
		// has to, because a hung provider signals nothing at all.
		assertThat(doneModel(events)).isEqualTo("fallback");
	}

	@Test
	void fallsBackWhenThePrimaryCompletesWithoutSayingAnything() {
		StubChatModel fallback = StubChatModel.answering("Fallback.");

		List<ServerSentEvent<Object>> events = events(StubChatModel.silent(), fallback);

		// A safety filter: technically a success, but there is no answer in it. Treating it as an
		// answer would stream an empty response and call it done.
		assertThat(doneModel(events)).isEqualTo("fallback");
	}

	@Test
	void sendsBothModelsTheSamePrompt() {
		StubChatModel fallback = StubChatModel.answering("Fallback.");

		events(StubChatModel.failingBeforeFirstToken(), fallback);

		// The fallback answers the same question under the same rules. Re-retrieving or
		// re-prompting for it would make the two models answer subtly different questions, and
		// `done.model` would stop being a safe thing to ignore.
		assertThat(fallback.lastPromptText()).contains("What does Saq build?");
	}

	@Test
	void doesNotCallTheFallbackWhenThePrimaryAnswers() {
		StubChatModel fallback = StubChatModel.answering("Never used.");

		events(StubChatModel.answering("Primary answer."), fallback);

		// The fallback is a different provider with its own bill and its own quota; calling it on
		// every request would double the cost of the feature for nothing.
		assertThat(fallback.calls()).isZero();
	}

	@Test
	void raisesUpstreamUnavailableWhenNeitherModelAnswers() {
		// Thrown rather than streamed, and that is the whole point: nothing has been written, so
		// the handler can still answer 503 with a Problem body as the contract promises. Once an
		// SSE response is committed there is no status code left to send.
		assertThatThrownBy(() -> newService(StubChatModel.failingBeforeFirstToken(),
				StubChatModel.failingBeforeFirstToken()).stream(question()))
				.isInstanceOf(UpstreamUnavailableException.class);
	}

	@Test
	void raisesUpstreamUnavailableWhenBothModelsHang() {
		assertThatThrownBy(() -> newService(StubChatModel.hanging(), StubChatModel.hanging())
				.stream(question()))
				.isInstanceOf(UpstreamUnavailableException.class);
	}

	@Test
	void endsWithAnErrorEventWhenTheModelFailsMidStream() {
		List<ServerSentEvent<Object>> events = events(
				StubChatModel.failingMidStream("Half an ", "answer"), StubChatModel.answering("unused"));

		// The tier is settled once the first token is out, so there is no switching here: the
		// visitor has already read half an answer, and a second model would start a new one.
		assertThat(events).extracting(ServerSentEvent::event)
				.containsExactly("token", "token", "error");
	}

	@Test
	void theMidStreamErrorEventCarriesAProblem() {
		List<ServerSentEvent<Object>> events = events(
				StubChatModel.failingMidStream("Half an answer"), StubChatModel.silent());

		ProblemDetail problem = (ProblemDetail) dataOf(events, "error");
		// The same RFC 9457 shape as every other error on this API, so a client has one way to
		// read failures rather than two.
		assertThat(problem.getStatus()).isEqualTo(503);
		assertThat(problem.getType()).hasToString(PROBLEM_BASE + "upstream-unavailable");
		assertThat(problem.getDetail()).isNotBlank();
	}

	@Test
	void doesNotSendDoneWhenTheAnswerWasCutShort() {
		List<ServerSentEvent<Object>> events = events(
				StubChatModel.failingMidStream("Half an answer"), StubChatModel.silent());

		// `done` is the client's only signal that an answer is complete. Sending it after an
		// error would tell the page a truncated answer is the whole answer.
		assertThat(events).extracting(ServerSentEvent::event).doesNotContain("done");
	}

	/** Collects the whole stream for one pair of models. */
	private List<ServerSentEvent<Object>> events(StubChatModel primary, StubChatModel fallback) {
		return newService(primary, fallback).stream(question()).collectList().block();
	}

	private ChatService newService(ChatModel primary, ChatModel fallback) {
		ChatProperties properties = new ChatProperties(5, 0.6, 8000, FIRST_TOKEN_TIMEOUT,
				"classpath:prompts/system.st", "classpath:knowledge-test/*.md", 10, false,
				"Saq", "/#contact");

		return new ChatService(ChatClient.builder(primary).build(), ChatClient.builder(fallback).build(),
				retrievalService, promptService, problemTypes, properties);
	}

	private static ChatRequest question() {
		return new ChatRequest(List.of(new ChatMessageRequest(ChatRole.USER, "What does Saq build?")));
	}

	/** The answer as the visitor would read it: every token event, concatenated. */
	private static String answerText(List<ServerSentEvent<Object>> events) {
		return events.stream()
				.filter(event -> "token".equals(event.event()))
				.map(event -> ((TokenEvent) event.data()).text())
				.reduce("", String::concat);
	}

	private static String doneModel(List<ServerSentEvent<Object>> events) {
		return ((DoneEvent) dataOf(events, "done")).model();
	}

	private static Object dataOf(List<ServerSentEvent<Object>> events, String name) {
		return events.stream()
				.filter(event -> name.equals(event.event()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no " + name + " event in the stream"))
				.data();
	}

}
