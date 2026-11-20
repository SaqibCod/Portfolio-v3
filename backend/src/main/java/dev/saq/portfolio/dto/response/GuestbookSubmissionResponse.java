package dev.saq.portfolio.dto.response;

import java.util.UUID;

/**
 * The 202 body for an accepted guestbook entry, matching the contract's
 * {@code GuestbookSubmission}.
 *
 * <p>{@code status} is always {@code "pending"}: nothing posted here is ever public on arrival,
 * and the contract pins the field to that single value. 202 rather than 201 for the same reason —
 * the entry exists, but the resource a visitor cares about (a note on the page) has not been
 * created and may never be.
 *
 * @param id a reference for the submission
 */
public record GuestbookSubmissionResponse(UUID id, String status) {

	private static final String PENDING = "pending";

	public static GuestbookSubmissionResponse pending(UUID id) {
		return new GuestbookSubmissionResponse(id, PENDING);
	}

}
