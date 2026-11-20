package dev.saq.portfolio.exception;

/**
 * A pagination cursor could not be decoded. Mapped to 400.
 *
 * <p>A cursor is an opaque token the API itself issued, so a client has nothing to fix in it —
 * the only sensible recovery is to start the listing again without one, which is what the detail
 * message says. Its contents are never echoed back: a cursor is attacker-controlled input, and
 * reflecting it would turn a malformed value into a way to put arbitrary text in a response.
 */
public class InvalidCursorException extends RuntimeException {

	public InvalidCursorException(String message) {
		super(message);
	}

	public InvalidCursorException(String message, Throwable cause) {
		super(message, cause);
	}

}
