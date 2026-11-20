package dev.saq.portfolio.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry as the owner sees it, matching the contract's {@code AdminGuestbookEntry}: the public
 * fields plus the two that only moderation cares about.
 *
 * <p>Still no {@code ipHash}. It exists to rate-limit submissions, not to be shown to anyone, and
 * nothing on the admin page needs it.
 *
 * @param status      the lowercase status the contract uses
 * @param moderatedAt when the decision was made, or {@code null} while the entry is pending
 */
public record AdminGuestbookEntryResponse(
		UUID id,
		String name,
		String message,
		Instant createdAt,
		String status,
		Instant moderatedAt) {
}
