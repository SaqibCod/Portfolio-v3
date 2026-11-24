package dev.saq.testsupport;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * A scriptable {@link ChatModel} that answers from a list of strings instead of from a provider.
 *
 * <p>Every failure the fallback logic exists to survive is reproducible here, and none of them
 * needs a network, a key or a clock the test has to wait on:
 * <ul>
 * <li>{@link #answering} — a normal answer, one token per element.</li>
 * <li>{@link #failingBeforeFirstToken} — a 429 or an expired key: the stream errors with nothing
 * emitted, which is the case the tier switch is built for.</li>
 * <li>{@link #silent} — a safety block: the stream completes having said nothing, which must
 * count as a failure rather than as an empty answer.</li>
 * <li>{@link #hanging} — a cold start that never returns, so the first-token timeout is what ends
 * the attempt.</li>
 * <li>{@link #failingMidStream} — a connection dropped halfway through, the one failure that
 * cannot be retried on another model because the visitor is already reading.</li>
 * </ul>
 *
 * <p>The last prompt is recorded, so tests can assert on what the model was actually told — that
 * the retrieved context reached the system message, that the conversation was replayed as
 * separate turns, that a visitor's text never became an instruction.
 */
public final class StubChatModel implements ChatModel {

	private final List<String> tokens;

	private final Behaviour behaviour;

	private final AtomicInteger calls = new AtomicInteger();

	private volatile Prompt lastPrompt;

	private StubChatModel(List<String> tokens, Behaviour behaviour) {
		this.tokens = tokens;
		this.behaviour = behaviour;
	}

	/** Streams these tokens, in order, then completes. */
	public static StubChatModel answering(String... tokens) {
		return new StubChatModel(List.of(tokens), Behaviour.ANSWER);
	}

	/** Errors immediately, before emitting anything. */
	public static StubChatModel failingBeforeFirstToken() {
		return new StubChatModel(List.of(), Behaviour.FAIL_IMMEDIATELY);
	}

	/** Completes without emitting a single token. */
	public static StubChatModel silent() {
		return new StubChatModel(List.of(), Behaviour.COMPLETE_EMPTY);
	}

	/** Never emits and never completes, so only a timeout ends the attempt. */
	public static StubChatModel hanging() {
		return new StubChatModel(List.of(), Behaviour.HANG);
	}

	/** Emits these tokens and then errors instead of completing. */
	public static StubChatModel failingMidStream(String... tokens) {
		return new StubChatModel(List.of(tokens), Behaviour.FAIL_AFTER_TOKENS);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		this.lastPrompt = prompt;
		this.calls.incrementAndGet();
		return response(String.join("", tokens));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		this.lastPrompt = prompt;
		this.calls.incrementAndGet();

		return switch (behaviour) {
			case ANSWER -> Flux.fromIterable(tokens).map(StubChatModel::response);
			case FAIL_IMMEDIATELY -> Flux.error(new IllegalStateException("429 Too Many Requests"));
			case COMPLETE_EMPTY -> Flux.empty();
			// Far longer than any test's configured first-token timeout, and never actually
			// awaited: the attempt is abandoned and the delay is discarded with it.
			case HANG -> Flux.<ChatResponse>never().delaySubscription(Duration.ofMinutes(5));
			case FAIL_AFTER_TOKENS -> Flux.fromIterable(tokens)
					.map(StubChatModel::response)
					.concatWith(Flux.error(new IllegalStateException("upstream closed the connection")));
		};
	}

	/** How many times this model was asked to answer — zero proves it was never reached. */
	public int calls() {
		return calls.get();
	}

	/** The prompt from the most recent call, or {@code null} if there has not been one. */
	public Prompt lastPrompt() {
		return lastPrompt;
	}

	/** Everything the model was told, system message included, as one string. */
	public String lastPromptText() {
		return lastPrompt == null ? "" : lastPrompt.getContents();
	}

	private static ChatResponse response(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
				ChatGenerationMetadata.NULL)));
	}

	private enum Behaviour {

		ANSWER, FAIL_IMMEDIATELY, COMPLETE_EMPTY, HANG, FAIL_AFTER_TOKENS

	}

}
