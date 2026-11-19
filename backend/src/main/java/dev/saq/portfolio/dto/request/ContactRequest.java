package dev.saq.portfolio.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A contact-form submission.
 *
 * <p>Every constraint mirrors the {@code ContactRequest} schema in
 * {@code docs/api/portfolio-api.yaml}; the column widths in {@code V2__contact.sql} match too, so
 * anything this accepts fits the table. Violations become a 400 listing the offending fields,
 * built by {@link dev.saq.portfolio.exception.GlobalExceptionHandler}.
 *
 * @param name           who is writing
 * @param email          where a reply should go; becomes the notification's {@code Reply-To}
 * @param subject        optional one-line summary
 * @param message        the message itself
 * @param turnstileToken the Cloudflare Turnstile token proving the sender is not a bot
 * @param website        honeypot. Hidden from humans, so a real visitor always leaves it empty;
 *                       anything here means the submission is silently dropped. Deliberately
 *                       unvalidated — rejecting it with a 400 would tell a bot it was spotted.
 */
public record ContactRequest(
		@NotBlank(message = "is required")
		@Size(max = 100, message = "must be at most 100 characters")
		String name,

		@NotBlank(message = "is required")
		@Email(message = "must be a valid email address")
		@Size(max = 254, message = "must be at most 254 characters")
		String email,

		@Size(max = 150, message = "must be at most 150 characters")
		String subject,

		@NotBlank(message = "is required")
		@Size(min = 10, max = 5000, message = "must be between 10 and 5000 characters")
		String message,

		@NotBlank(message = "is required")
		String turnstileToken,

		String website) {

	/** True when the honeypot was filled in, which only a bot does. */
	public boolean isHoneypotTripped() {
		return website != null && !website.isBlank();
	}

}
