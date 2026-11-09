package dev.saq.portfolio.model;

import java.time.Duration;

/**
 * The rate-limit budgets the API enforces, one per kind of endpoint.
 *
 * <p>Each policy is a token bucket refilled in whole windows (not continuously), so a visitor
 * gets a predictable "n requests per window" allowance rather than a trickle.
 */
public enum RateLimitPolicy {

	/** Chatbot questions: expensive, so kept deliberately low. */
	CHAT(20, Duration.ofHours(1)),

	/** Contact form submissions. */
	CONTACT(3, Duration.ofHours(1)),

	/** Guestbook entries. */
	GUESTBOOK(2, Duration.ofDays(1)),

	/** Anything read-only and cacheable. */
	PUBLIC_READ(120, Duration.ofMinutes(1));

	private final long limit;

	private final Duration window;

	RateLimitPolicy(long limit, Duration window) {
		this.limit = limit;
		this.window = window;
	}

	/** Requests allowed per window. */
	public long limit() {
		return limit;
	}

	public Duration window() {
		return window;
	}

}
