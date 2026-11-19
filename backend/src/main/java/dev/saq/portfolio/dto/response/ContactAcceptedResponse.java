package dev.saq.portfolio.dto.response;

import java.util.UUID;

/**
 * The 202 body for an accepted contact message, matching the contract's {@code ContactAccepted}.
 *
 * <p>{@code status} is always {@code "queued"}: the response is sent before the mail provider has
 * been asked, so delivery is never confirmed here. The contract pins it to that single value.
 *
 * @param id a reference for the submission
 */
public record ContactAcceptedResponse(UUID id, String status) {

	private static final String QUEUED = "queued";

	public static ContactAcceptedResponse queued(UUID id) {
		return new ContactAcceptedResponse(id, QUEUED);
	}

}
