package dev.saq.portfolio.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One note a visitor left in the guestbook.
 *
 * <p>A record, so a stored entry is immutable in memory: moderation advances the status with a
 * single {@code update} in the repository rather than by mutating and re-saving the aggregate,
 * which keeps a decision from overwriting the note itself.
 *
 * <p>Holds a hashed IP only, never the address itself, and {@code ipHash} never leaves the
 * service layer — neither response DTO carries it.
 *
 * @param id          {@code null} before the insert; filled in from the database default
 * @param createdAt   set by the database default, so it is never written from here
 * @param moderatedAt when the owner decided, or {@code null} while the entry is still pending
 */
@Table("guestbook_entry")
public record GuestbookEntry(
		@Id UUID id,
		String name,
		String message,
		GuestbookStatus status,
		String ipHash,
		@ReadOnlyProperty Instant createdAt,
		Instant moderatedAt) {

	/** An entry as it looks before it is stored: no id, no timestamps, awaiting approval. */
	public static GuestbookEntry pending(String name, String message, String ipHash) {
		return new GuestbookEntry(null, name, message, GuestbookStatus.PENDING, ipHash, null, null);
	}

}
