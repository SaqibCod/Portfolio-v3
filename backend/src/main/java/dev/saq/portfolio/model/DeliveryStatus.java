package dev.saq.portfolio.model;

/**
 * How far a stored message has got on its way to the owner's inbox.
 *
 * <p>The names are persisted as-is and are pinned by a {@code check} constraint in
 * {@code V2__contact.sql}, so renaming a constant requires a migration.
 */
public enum DeliveryStatus {

	/** Stored and handed to the mail provider, which has not answered yet. */
	QUEUED,

	/** The provider accepted the message. */
	SENT,

	/** The provider rejected it or could not be reached; the row is the only remaining copy. */
	FAILED

}
