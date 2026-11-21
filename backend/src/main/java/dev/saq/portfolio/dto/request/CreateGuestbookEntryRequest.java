package dev.saq.portfolio.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A guestbook submission.
 *
 * <p>Every constraint mirrors the {@code CreateGuestbookEntry} schema in
 * {@code docs/api/portfolio-api.yaml}; the column widths in {@code V3__guestbook.sql} match too,
 * so anything this accepts fits the table. Violations become a 400 listing the offending fields,
 * built by {@link dev.saq.portfolio.exception.GlobalExceptionHandler}.
 *
 * <p>No email address and nothing else identifying: the guestbook is anonymous by design, and the
 * only thing stored beyond the note is a hashed IP for rate limiting.
 *
 * @param name           how the visitor would like to be credited
 * @param message        the note itself
 * @param turnstileToken the Cloudflare Turnstile token proving the visitor is not a bot
 * @param website        honeypot. Hidden from humans, so a real visitor always leaves it empty;
 *                       anything here means the entry is silently dropped. Deliberately
 *                       unvalidated — rejecting it with a 400 would tell a bot it was spotted.
 */
public record CreateGuestbookEntryRequest(
		@NotBlank(message = "is required")
		@Size(max = 50, message = "must be at most 50 characters")
		String name,

		@NotBlank(message = "is required")
		@Size(min = 2, max = 500, message = "must be between 2 and 500 characters")
		String message,

		@NotBlank(message = "is required")
		String turnstileToken,

		String website) {

	/** True when the honeypot was filled in, which only a bot does. */
	public boolean isHoneypotTripped() {
		return website != null && !website.isBlank();
	}

}
