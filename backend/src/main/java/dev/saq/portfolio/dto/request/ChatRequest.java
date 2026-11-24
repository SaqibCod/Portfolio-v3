package dev.saq.portfolio.dto.request;

import java.util.List;

import dev.saq.portfolio.model.ChatRole;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * A question, plus as much of the conversation as the browser wants the model to remember.
 *
 * <p>The endpoint is stateless by design (phase 08 keeps conversation storage out of scope), so
 * the client owns the history and resends it. That makes the ten-message cap the only thing
 * standing between a visitor and an arbitrarily long prompt, which is why it is enforced here
 * rather than trimmed quietly later.
 *
 * <p>Matches {@code ChatRequest} in {@code docs/api/portfolio-api.yaml}.
 *
 * @param messages oldest first, at least one and at most ten, the last of them from the visitor
 */
public record ChatRequest(
		@NotEmpty @Size(max = 10) List<@Valid ChatMessageRequest> messages) {

	/**
	 * The question to answer: the last message, which has to be the visitor's.
	 *
	 * <p>A conversation ending in an assistant message is not a request for anything — there is
	 * nothing to retrieve against and nothing to answer. Rejecting it as a field error keeps that
	 * a 400 with the same body shape as every other validation failure, rather than a 500 from
	 * retrieval or, worse, a model asked to continue its own sentence.
	 *
	 * <p>Passes when there are no messages at all, deferring to {@code @NotEmpty} on the list.
	 * Both constraints reporting the same empty array would hand the client two errors to fix
	 * and one actual problem.
	 */
	@JsonIgnore
	@AssertTrue(message = "must end with a message from the user")
	public boolean isEndingWithUserMessage() {
		return messages == null || messages.isEmpty() || messages.getLast().role() == ChatRole.USER;
	}

	/** The visitor's question, which is what retrieval searches on. */
	@JsonIgnore
	public String question() {
		return messages.getLast().content();
	}

	/**
	 * The trailing {@code max} messages, oldest first.
	 *
	 * <p>Trailing rather than leading: the model needs the turns closest to the question, and the
	 * opening of a long conversation is the first thing that can be dropped.
	 */
	@JsonIgnore
	public List<ChatMessageRequest> recentMessages(int max) {
		return messages.size() <= max ? messages : messages.subList(messages.size() - max, messages.size());
	}

}
