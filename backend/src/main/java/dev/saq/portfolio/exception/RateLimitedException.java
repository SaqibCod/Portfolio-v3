package dev.saq.portfolio.exception;

/** A rate-limit policy was exceeded. Mapped to 429 with a {@code Retry-After} header. */
public class RateLimitedException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitedException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/** Seconds the client must wait before retrying; at least 1. */
	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
