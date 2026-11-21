package dev.saq.portfolio.model;

/**
 * Where a guestbook entry stands with the owner.
 *
 * <p>The constant names are persisted as-is and are pinned by a {@code check} constraint in
 * {@code V3__guestbook.sql}, so renaming one requires a migration.
 *
 * <p>The API spells these in lowercase ({@code pending}) while the column holds the uppercase
 * names, and this enum is the only place that bridges the two: {@link #toApi()} going out,
 * {@link #fromApi(String)} coming in. Keeping the mapping here means no controller, mapper or
 * query has to know that the two spellings differ.
 */
public enum GuestbookStatus {

	/** Stored and waiting for the owner. Invisible to everyone but the owner. */
	PENDING,

	/** Approved by the owner; this is the only status the public list serves. */
	APPROVED,

	/** Turned down. Kept rather than deleted, so the same note cannot simply be resubmitted. */
	REJECTED;

	/** The lowercase spelling the contract uses, e.g. {@code "pending"}. */
	public String toApi() {
		return name().toLowerCase();
	}

	/**
	 * The status an API value names.
	 *
	 * @param value a lowercase status from the contract; leading and trailing space is tolerated
	 * @throws IllegalArgumentException if {@code value} is not one of the three. Registered as a
	 *                                 web converter in {@link dev.saq.portfolio.config.WebMvcConfig},
	 *                                 where Spring turns that into a 400 rather than a 500.
	 */
	public static GuestbookStatus fromApi(String value) {
		if (value == null) {
			throw new IllegalArgumentException("status is required");
		}
		return switch (value.strip().toLowerCase()) {
			case "pending" -> PENDING;
			case "approved" -> APPROVED;
			case "rejected" -> REJECTED;
			default -> throw new IllegalArgumentException(
					"status must be one of pending, approved, rejected");
		};
	}

}
