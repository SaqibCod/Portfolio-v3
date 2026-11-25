package dev.saq.testsupport;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * A {@link ChatModel} bean whose behaviour a test can replace between cases.
 *
 * <p>Exists because the thing under test is a <em>pair</em> of models and which one answers. That
 * decision is made when {@code ChatService} is constructed with two {@code ChatClient} beans, so
 * the models have to be in the application context — but each case needs different behaviour from
 * them: primary answers, primary 429s, both fail, the answer dies halfway through. A fixed stub
 * would need a context per case, and {@code @MockitoBean} would too.
 *
 * <p>So the bean is a switch. One context, swapped behaviour per test, and
 * {@link #reset} in a {@code @BeforeEach} to keep cases from leaking into each other.
 *
 * <p>The default is {@link StubChatModel#silent()} rather than a working answer: a test that
 * forgets to script its models fails loudly instead of passing on a response nobody wrote.
 */
public class SwitchableChatModel implements ChatModel {

	private volatile StubChatModel delegate = StubChatModel.silent();

	/** Points this bean at a new behaviour for the rest of the test. */
	public void set(StubChatModel delegate) {
		this.delegate = delegate;
	}

	/** Back to answering nothing, so the next test starts from a known state. */
	public void reset() {
		this.delegate = StubChatModel.silent();
	}

	/** The behaviour currently in place, for asserting on calls and on the prompt it received. */
	public StubChatModel current() {
		return delegate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return delegate.call(prompt);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return delegate.stream(prompt);
	}

}
