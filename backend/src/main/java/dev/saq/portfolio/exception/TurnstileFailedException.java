package dev.saq.portfolio.exception;

/** Cloudflare Turnstile rejected the submitted token. Mapped to 422. */
public class TurnstileFailedException extends RuntimeException {

	public TurnstileFailedException(String message) {
		super(message);
	}

}
