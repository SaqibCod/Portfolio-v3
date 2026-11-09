package dev.saq.portfolio.exception;

import java.net.URI;

/**
 * Stable {@code type} URIs for RFC 9457 problem responses.
 *
 * <p>These are identifiers, not endpoints: clients match on them, so a value must never change
 * once released. The base matches the contract's documented example
 * ({@code docs/api/portfolio-api.yaml}, {@code Problem.type}).
 */
public final class ProblemTypes {

	private static final String BASE = "https://api.example.com/problems/";

	/** 400 — request body or parameters failed validation. */
	public static final URI VALIDATION = URI.create(BASE + "validation");

	/** 401 — missing or invalid admin token. */
	public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");

	/** 404 — no such resource. */
	public static final URI NOT_FOUND = URI.create(BASE + "not-found");

	/** 422 — Cloudflare Turnstile rejected the token. */
	public static final URI TURNSTILE_FAILED = URI.create(BASE + "turnstile-failed");

	/** 429 — a rate-limit policy was exceeded. */
	public static final URI RATE_LIMITED = URI.create(BASE + "rate-limited");

	/** 503 — an upstream dependency is unavailable and there is no cached data. */
	public static final URI UPSTREAM_UNAVAILABLE = URI.create(BASE + "upstream-unavailable");

	/** 500 — anything unexpected. Details are logged, never returned. */
	public static final URI INTERNAL = URI.create(BASE + "internal");

	private ProblemTypes() {
	}

}
