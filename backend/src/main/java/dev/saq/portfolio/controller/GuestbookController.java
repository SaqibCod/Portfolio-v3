package dev.saq.portfolio.controller;

import dev.saq.portfolio.annotation.RateLimited;
import dev.saq.portfolio.dto.request.CreateGuestbookEntryRequest;
import dev.saq.portfolio.dto.response.GuestbookPageResponse;
import dev.saq.portfolio.dto.response.GuestbookSubmissionResponse;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.GuestbookService;
import dev.saq.portfolio.util.ClientIpUtils;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** The guestbook's two public endpoints: read the approved notes, or leave one. */
@RestController
@RequestMapping("/api/v1/guestbook")
public class GuestbookController {

	/** The contract's {@code limit} default and bounds. */
	private static final String DEFAULT_LIMIT = "20";

	private final GuestbookService guestbookService;

	private final ClientIpUtils clientIpUtils;

	public GuestbookController(GuestbookService guestbookService, ClientIpUtils clientIpUtils) {
		this.guestbookService = guestbookService;
		this.clientIpUtils = clientIpUtils;
	}

	/**
	 * One page of approved entries, newest first.
	 *
	 * <p>Only approved entries are reachable here — there is no status parameter to widen, so a
	 * pending note cannot be read by guessing a query string.
	 *
	 * @param cursor the {@code nextCursor} from a previous page, or absent for the first one
	 * @param limit  page size, 1–50; out-of-range values are a 400 rather than being clamped,
	 *               because a client asking for 500 has a bug worth telling it about
	 */
	@GetMapping
	@RateLimited(RateLimitPolicy.PUBLIC_READ)
	public GuestbookPageResponse list(
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = DEFAULT_LIMIT)
			@Min(value = 1, message = "must be at least 1")
			@Max(value = 50, message = "must be at most 50") int limit) {

		return guestbookService.listApproved(cursor, limit);
	}

	/**
	 * Takes one note and holds it for approval.
	 *
	 * <p>202 rather than 201: the entry is stored, but the thing a visitor is asking for — a note
	 * on the page — has not been created and may never be. Reading the caller's address is an HTTP
	 * concern and belongs here; what is done with it is the service's.
	 *
	 * <p>{@code @RateLimited(GUESTBOOK)} caps a caller at two entries a day, enforced by
	 * {@link dev.saq.portfolio.interceptor.RateLimitInterceptor} before this method runs.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@RateLimited(RateLimitPolicy.GUESTBOOK)
	public GuestbookSubmissionResponse sign(@Valid @RequestBody CreateGuestbookEntryRequest request,
			HttpServletRequest httpRequest) {

		return guestbookService.create(request, clientIpUtils.resolveOrNull(httpRequest));
	}

}
