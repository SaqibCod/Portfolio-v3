package dev.saq.portfolio.dto.request;

import dev.saq.portfolio.model.ChatRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One message in a chat conversation, as the browser sends it.
 *
 * <p>Matches {@code ChatMessage} in {@code docs/api/portfolio-api.yaml}. The 1,000-character
 * ceiling is the contract's, and it is a cost control as much as a validation rule: the endpoint
 * accepts up to ten of these, so the limit bounds how much a single request can put in front of
 * a billed model.
 *
 * @param role    who said it. An unknown value is a 400 from Jackson rather than a silently
 *                ignored message
 * @param content the text. Never blank — an empty question has no answer
 */
public record ChatMessageRequest(
		@NotNull ChatRole role,
		@NotBlank @Size(max = 1000) String content) {

}
