package dev.saq.portfolio.exception;

/**
 * An upstream dependency (LLM, GitHub API, mail provider) failed and no cached data could be
 * served. Mapped to 503 with a {@code Retry-After} header.
 */
public class UpstreamUnavailableException extends RuntimeException {

	/** Default wait suggested to clients when the caller has no better estimate. */
	public static final long DEFAULT_RETRY_AFTER_SECONDS = 30;

	private final long retryAfterSeconds;

	public UpstreamUnavailableException(String message) {
		this(message, DEFAULT_RETRY_AFTER_SECONDS, null);
	}

	public UpstreamUnavailableException(String message, Throwable cause) {
		this(message, DEFAULT_RETRY_AFTER_SECONDS, cause);
	}

	public UpstreamUnavailableException(String message, long retryAfterSeconds, Throwable cause) {
		super(message, cause);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
