package dev.saq.portfolio.exception;

import java.net.URI;

import dev.saq.portfolio.config.AppProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@code type} URIs used in RFC 9457 problem responses, all of the form
 * {@code {app.api.base-url}/problems/{slug}}.
 *
 * <p>The base comes from configuration ({@code API_BASE_URL}) so the real domain can be set per
 * environment without touching code; the {@code /problems/{slug}} structure and every slug are
 * fixed here. Slugs are published identifiers that clients match on, so a slug must never change
 * once released — only the base may differ between environments.
 */
@Component
public class ProblemTypes {

	/** Path segment every problem type sits under. */
	static final String PROBLEMS_PATH = "/problems/";

	/** 400 — request body or parameters failed validation. */
	public static final String VALIDATION_SLUG = "validation";

	/** 401 — missing or invalid admin token. */
	public static final String UNAUTHORIZED_SLUG = "unauthorized";

	/** 404 — no such resource. */
	public static final String NOT_FOUND_SLUG = "not-found";

	/** 422 — Cloudflare Turnstile rejected the token. */
	public static final String TURNSTILE_FAILED_SLUG = "turnstile-failed";

	/** 429 — a rate-limit policy was exceeded. */
	public static final String RATE_LIMITED_SLUG = "rate-limited";

	/** 503 — an upstream dependency is unavailable and there is no cached data. */
	public static final String UPSTREAM_UNAVAILABLE_SLUG = "upstream-unavailable";

	/** 500 — anything unexpected. Details are logged, never returned. */
	public static final String INTERNAL_SLUG = "internal";

	private final URI validation;

	private final URI unauthorized;

	private final URI notFound;

	private final URI turnstileFailed;

	private final URI rateLimited;

	private final URI upstreamUnavailable;

	private final URI internal;

	/** Marked explicitly: there are two constructors, so Spring cannot deduce which to use. */
	@Autowired
	public ProblemTypes(AppProperties properties) {
		this(properties.api().baseUrl());
	}

	/** Package-private so tests can pin the URI shape without building a whole AppProperties. */
	ProblemTypes(String baseUrl) {
		String base = stripTrailingSlash(baseUrl) + PROBLEMS_PATH;
		this.validation = URI.create(base + VALIDATION_SLUG);
		this.unauthorized = URI.create(base + UNAUTHORIZED_SLUG);
		this.notFound = URI.create(base + NOT_FOUND_SLUG);
		this.turnstileFailed = URI.create(base + TURNSTILE_FAILED_SLUG);
		this.rateLimited = URI.create(base + RATE_LIMITED_SLUG);
		this.upstreamUnavailable = URI.create(base + UPSTREAM_UNAVAILABLE_SLUG);
		this.internal = URI.create(base + INTERNAL_SLUG);
	}

	public URI validation() {
		return validation;
	}

	public URI unauthorized() {
		return unauthorized;
	}

	public URI notFound() {
		return notFound;
	}

	public URI turnstileFailed() {
		return turnstileFailed;
	}

	public URI rateLimited() {
		return rateLimited;
	}

	public URI upstreamUnavailable() {
		return upstreamUnavailable;
	}

	public URI internal() {
		return internal;
	}

	/** Keeps {@code https://host} and {@code https://host/} producing the same type URIs. */
	private static String stripTrailingSlash(String baseUrl) {
		String trimmed = baseUrl.strip();
		return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
	}

}
