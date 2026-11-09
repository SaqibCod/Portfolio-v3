package dev.saq.portfolio.exception;

/** A requested resource does not exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
