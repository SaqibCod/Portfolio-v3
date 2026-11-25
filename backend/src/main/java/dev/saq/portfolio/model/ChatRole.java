package dev.saq.portfolio.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Who said a message in a chat request.
 *
 * <p>Only the two roles the contract allows. The system role is deliberately absent: the system
 * prompt is assembled server-side by
 * {@link dev.saq.portfolio.service.PromptService} and a client that could send one could rewrite
 * the rules the assistant answers under.
 *
 * <p>The wire values are lowercase to match {@code ChatMessage.role} in the contract, which is
 * also what every chat API uses.
 */
public enum ChatRole {

	/** A visitor's question. */
	@JsonProperty("user")
	USER,

	/** An answer the assistant gave earlier in the same conversation. */
	@JsonProperty("assistant")
	ASSISTANT

}
