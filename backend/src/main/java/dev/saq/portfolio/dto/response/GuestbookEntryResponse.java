package dev.saq.portfolio.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One approved entry as the public list serves it, matching the contract's
 * {@code GuestbookEntry}.
 *
 * <p>Carries no status: this shape only ever describes approved entries, so a status field would
 * be the same value on every item. It also carries no {@code ipHash} and no {@code moderatedAt} —
 * the first is never exposed to anyone, the second only to the owner.
 */
public record GuestbookEntryResponse(UUID id, String name, String message, Instant createdAt) {
}
