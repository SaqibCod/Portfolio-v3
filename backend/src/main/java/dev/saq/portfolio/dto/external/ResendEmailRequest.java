package dev.saq.portfolio.dto.external;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST https://api.resend.com/emails}.
 *
 * <p>Third-party shape, so the field names are Resend's. {@code replyTo} is omitted entirely when
 * absent rather than sent as null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendEmailRequest(
		String from,
		List<String> to,
		String subject,
		String text,
		@JsonProperty("reply_to") String replyTo) {
}
