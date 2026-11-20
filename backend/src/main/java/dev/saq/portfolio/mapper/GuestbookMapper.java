package dev.saq.portfolio.mapper;

import java.util.List;

import dev.saq.portfolio.dto.request.CreateGuestbookEntryRequest;
import dev.saq.portfolio.dto.response.AdminGuestbookEntryResponse;
import dev.saq.portfolio.dto.response.GuestbookEntryResponse;
import dev.saq.portfolio.model.GuestbookEntry;

import org.springframework.stereotype.Component;

/**
 * Converts between the guestbook's request and response DTOs and {@link GuestbookEntry}.
 *
 * <p>Two outgoing shapes, deliberately separate rather than one with optional fields: the public
 * one cannot carry a status or a moderation time even by accident, which is the property that
 * keeps a pending note off the site.
 */
@Component
public class GuestbookMapper {

	/**
	 * A submission as a not-yet-approved row.
	 *
	 * <p>The honeypot and the Turnstile token are deliberately dropped: neither is part of the
	 * note, and the token is a single-use credential that must not be stored.
	 *
	 * <p>Name and message are stripped here. Surrounding whitespace is never meaningful in either,
	 * and a note padded with newlines would otherwise set its own spacing on the page.
	 *
	 * @param ipHash the already-hashed caller address; this never sees a raw IP
	 */
	public GuestbookEntry toEntity(CreateGuestbookEntryRequest request, String ipHash) {
		return GuestbookEntry.pending(request.name().strip(), request.message().strip(), ipHash);
	}

	/** One entry as the public list serves it. */
	public GuestbookEntryResponse toResponse(GuestbookEntry entry) {
		return new GuestbookEntryResponse(entry.id(), entry.name(), entry.message(),
				entry.createdAt());
	}

	/** One entry as the owner sees it, with the status spelled the way the contract does. */
	public AdminGuestbookEntryResponse toAdminResponse(GuestbookEntry entry) {
		return new AdminGuestbookEntryResponse(entry.id(), entry.name(), entry.message(),
				entry.createdAt(), entry.status().toApi(), entry.moderatedAt());
	}

	public List<GuestbookEntryResponse> toResponses(List<GuestbookEntry> entries) {
		return entries.stream().map(this::toResponse).toList();
	}

	public List<AdminGuestbookEntryResponse> toAdminResponses(List<GuestbookEntry> entries) {
		return entries.stream().map(this::toAdminResponse).toList();
	}

}
