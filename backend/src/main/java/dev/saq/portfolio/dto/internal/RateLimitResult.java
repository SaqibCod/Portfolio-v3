package dev.saq.portfolio.dto.internal;

/**
 * Outcome of one rate-limit check. Internal only: it becomes response headers, never a response
 * body.
 *
 * @param allowed            whether the request may proceed
 * @param limit              requests allowed per window, for {@code RateLimit-Limit}
 * @param remaining          requests left in the window, for {@code RateLimit-Remaining}
 * @param retryAfterSeconds  seconds until the next token, for {@code Retry-After}; 0 when allowed
 */
public record RateLimitResult(boolean allowed, long limit, long remaining, long retryAfterSeconds) {

	public static RateLimitResult allowed(long limit, long remaining) {
		return new RateLimitResult(true, limit, remaining, 0);
	}

	public static RateLimitResult rejected(long limit, long retryAfterSeconds) {
		return new RateLimitResult(false, limit, 0, retryAfterSeconds);
	}

}
