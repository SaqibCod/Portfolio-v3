package dev.saq.portfolio.controller;

import java.util.UUID;

import dev.saq.portfolio.dto.request.ModerateEntryRequest;
import dev.saq.portfolio.dto.response.AdminGuestbookEntryResponse;
import dev.saq.portfolio.dto.response.AdminGuestbookPageResponse;
import dev.saq.portfolio.model.GuestbookStatus;
import dev.saq.portfolio.service.GuestbookService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Moderation, for the site owner only.
 *
 * <p>Nothing here checks the token: every path under {@code /api/v1/admin/} is already behind
 * {@link dev.saq.portfolio.security.AdminTokenFilter}, which answers 401 before Spring MVC picks
 * a handler. Repeating the check in each method would mean three places to get it wrong.
 */
@RestController
@RequestMapping("/api/v1/admin/guestbook")
public class AdminGuestbookController {

	/** The contract's {@code limit} default and bounds. */
	private static final String DEFAULT_LIMIT = "20";

	/** The contract's {@code status} default: the queue the owner actually opens this page for. */
	private static final String DEFAULT_STATUS = "pending";

	private final GuestbookService guestbookService;

	public AdminGuestbookController(GuestbookService guestbookService) {
		this.guestbookService = guestbookService;
	}

	/**
	 * One page of entries with the given status, newest first.
	 *
	 * @param status one of {@code pending}, {@code approved}, {@code rejected}. Bound through the
	 *               converter registered in {@link dev.saq.portfolio.config.WebMvcConfig}, so an
	 *               unknown value is a 400 rather than a 500.
	 */
	@GetMapping
	public AdminGuestbookPageResponse list(
			@RequestParam(defaultValue = DEFAULT_STATUS) GuestbookStatus status,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = DEFAULT_LIMIT)
			@Min(value = 1, message = "must be at least 1")
			@Max(value = 50, message = "must be at most 50") int limit) {

		return guestbookService.listByStatus(status, cursor, limit);
	}

	/**
	 * Approves or rejects one entry.
	 *
	 * <p>200 with the updated entry rather than 204: the owner's page re-renders the row straight
	 * from this response, so it needs the new status and the moderation time it was stamped with.
	 */
	@PatchMapping("/{entryId}")
	public AdminGuestbookEntryResponse moderate(@PathVariable UUID entryId,
			@Valid @RequestBody ModerateEntryRequest request) {

		return guestbookService.moderate(entryId, request.toStatus());
	}

	/** Deletes one entry for good. 204, so there is nothing left to describe. */
	@DeleteMapping("/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID entryId) {
		guestbookService.delete(entryId);
	}

}
