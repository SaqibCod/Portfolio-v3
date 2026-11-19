package dev.saq.portfolio.controller;

import dev.saq.portfolio.annotation.RateLimited;
import dev.saq.portfolio.dto.request.ContactRequest;
import dev.saq.portfolio.dto.response.ContactAcceptedResponse;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.ContactService;
import dev.saq.portfolio.util.ClientIpUtils;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** {@code POST /api/v1/contact} — the contact form's only endpoint. */
@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

	private final ContactService contactService;

	private final ClientIpUtils clientIpUtils;

	public ContactController(ContactService contactService, ClientIpUtils clientIpUtils) {
		this.contactService = contactService;
		this.clientIpUtils = clientIpUtils;
	}

	/**
	 * Accepts a message for delivery.
	 *
	 * <p>202 rather than 201: the message is stored, but the email has not been sent by the time
	 * this returns, so nothing is being reported as created. Reading the caller's address is an
	 * HTTP concern and belongs here; what is done with it is the service's.
	 *
	 * <p>{@code @RateLimited(CONTACT)} caps a caller at three submissions an hour, enforced by
	 * {@link dev.saq.portfolio.interceptor.RateLimitInterceptor} before this method runs.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@RateLimited(RateLimitPolicy.CONTACT)
	public ContactAcceptedResponse send(@Valid @RequestBody ContactRequest request,
			HttpServletRequest httpRequest) {

		return contactService.submit(request, clientIpUtils.resolveOrNull(httpRequest));
	}

}
