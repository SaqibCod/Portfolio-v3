package dev.saq.testsupport;

import dev.saq.portfolio.annotation.RateLimited;
import dev.saq.portfolio.exception.NotFoundException;
import dev.saq.portfolio.exception.RateLimitedException;
import dev.saq.portfolio.exception.TurnstileFailedException;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.model.RateLimitPolicy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Endpoints that exist only to exercise the cross-cutting infrastructure (error mapping, rate
 * limiting, the admin filter) while there are no feature endpoints yet.
 *
 * <p>It lives outside the {@code dev.saq.portfolio} scan root on purpose, so component scanning
 * never picks it up: tests that want these routes import {@link TestEndpointsConfig} explicitly.
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestEndpointsController {

	/** Plain success, with no rate-limit policy attached. */
	@GetMapping("/ok")
	String ok() {
		return "ok";
	}

	/** Rate limited with the smallest policy, so a test can exhaust it in two requests. */
	@GetMapping("/limited")
	@RateLimited(RateLimitPolicy.GUESTBOOK)
	String limited() {
		return "limited";
	}

	/** Rate limited with a generous policy, for asserting the headers on a normal response. */
	@GetMapping("/limited-read")
	@RateLimited(RateLimitPolicy.PUBLIC_READ)
	String limitedRead() {
		return "limited-read";
	}

	/** 400: bean validation on the request body. */
	@PostMapping("/validate")
	String validate(@Valid @RequestBody TestPayload payload) {
		return payload.name();
	}

	/** 404 */
	@GetMapping("/not-found")
	String notFound() {
		throw new NotFoundException("No such thing.");
	}

	/** 422 */
	@GetMapping("/turnstile-failed")
	String turnstileFailed() {
		throw new TurnstileFailedException("Turnstile rejected the token.");
	}

	/** 429 */
	@GetMapping("/rate-limited")
	String rateLimited() {
		throw new RateLimitedException("Slow down.", 42);
	}

	/** 503 */
	@GetMapping("/upstream-unavailable")
	String upstreamUnavailable() {
		throw new UpstreamUnavailableException("GitHub is down.", 17, null);
	}

	/** 500 */
	@GetMapping("/boom")
	String boom() {
		throw new IllegalStateException("Something internal that must never be exposed.");
	}

	/** Mirrors the shape of a real request DTO: a record with validation constraints. */
	public record TestPayload(
			@NotBlank String name,
			@Size(max = 10) String message) {
	}

}
