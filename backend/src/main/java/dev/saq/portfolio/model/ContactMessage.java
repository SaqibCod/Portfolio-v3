package dev.saq.portfolio.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One message a visitor sent through the contact form.
 *
 * <p>A record, so a stored message is immutable in memory: the delivery status is advanced with a
 * single {@code update} in the repository rather than by mutating and re-saving the aggregate,
 * which keeps the async callback from overwriting any other column.
 *
 * <p>Holds a hashed IP only, never the address itself.
 *
 * @param id        {@code null} before the insert; filled in from the database default
 * @param createdAt set by the database default, so it is never written from here
 */
@Table("contact_message")
public record ContactMessage(
		@Id UUID id,
		String name,
		String email,
		String subject,
		String message,
		String ipHash,
		DeliveryStatus status,
		@ReadOnlyProperty Instant createdAt) {

	/** A message as it looks before it is stored: no id, no timestamp, not yet delivered. */
	public static ContactMessage queued(String name, String email, String subject, String message,
			String ipHash) {
		return new ContactMessage(null, name, email, subject, message, ipHash, DeliveryStatus.QUEUED, null);
	}

}
