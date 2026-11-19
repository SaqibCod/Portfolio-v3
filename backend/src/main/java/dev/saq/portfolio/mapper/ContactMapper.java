package dev.saq.portfolio.mapper;

import dev.saq.portfolio.dto.request.ContactRequest;
import dev.saq.portfolio.dto.response.ContactAcceptedResponse;
import dev.saq.portfolio.model.ContactMessage;

import org.springframework.stereotype.Component;

/** Converts between {@link ContactRequest}/{@link ContactAcceptedResponse} and {@link ContactMessage}. */
@Component
public class ContactMapper {

	/**
	 * A submission as a not-yet-delivered row.
	 *
	 * <p>The honeypot and the Turnstile token are deliberately dropped: neither is part of the
	 * message, and the token is a single-use credential that must not be stored.
	 *
	 * @param ipHash the already-hashed caller address; this never sees a raw IP
	 */
	public ContactMessage toEntity(ContactRequest request, String ipHash) {
		return ContactMessage.queued(
				request.name().strip(),
				request.email().strip(),
				blankToNull(request.subject()),
				request.message().strip(),
				ipHash);
	}

	/** The 202 body for a stored message. */
	public ContactAcceptedResponse toResponse(ContactMessage message) {
		return ContactAcceptedResponse.queued(message.id());
	}

	/** An omitted subject and a whitespace-only one are the same thing: no subject. */
	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

}
