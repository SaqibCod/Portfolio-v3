package dev.saq.portfolio.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import dev.saq.portfolio.exception.InvalidCursorException;

import org.springframework.stereotype.Component;

/**
 * Encodes and decodes the opaque {@code cursor} the paginated endpoints hand out.
 *
 * <p>A cursor is base64url of {@code <createdAt ISO-8601>|<uuid>} — the two columns the keyset
 * queries in {@link dev.saq.portfolio.repository.GuestbookRepository} order and seek by. The
 * encoding is not a secret and is not meant to be one; it exists so that the contract can call
 * the value opaque and the pair inside it can change later without any client having parsed it.
 *
 * <p>Base64url (no padding) rather than plain base64, because the value travels in a query
 * string, where {@code +} means a space and {@code /} ends a path segment.
 *
 * <p>Every decode failure is the same {@link InvalidCursorException}, whatever went wrong. A
 * cursor is always one the API issued, so a malformed one is a bug or a hand-edited URL rather
 * than something a client can correct field by field.
 */
@Component
public class CursorCodec {

	private static final char SEPARATOR = '|';

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	/**
	 * The position of one entry in a page, as the keyset queries need it.
	 *
	 * @param createdAt the entry's creation time
	 * @param id        the entry's id, which breaks ties on {@code createdAt}
	 */
	public record Cursor(Instant createdAt, UUID id) {
	}

	/** The cursor pointing just past {@code createdAt}/{@code id}. */
	public String encode(Instant createdAt, UUID id) {
		String payload = createdAt.toString() + SEPARATOR + id;
		return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * The position a cursor names.
	 *
	 * @throws InvalidCursorException if the value is not base64url, does not hold exactly one
	 *                                separator, or either half does not parse
	 */
	public Cursor decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			throw new InvalidCursorException("Cursor is missing.");
		}

		String payload = decodeBase64(cursor);
		int separator = payload.indexOf(SEPARATOR);
		// lastIndexOf too, so a payload carrying a second separator is rejected rather than
		// silently parsed as far as the first one.
		if (separator < 0 || separator != payload.lastIndexOf(SEPARATOR)) {
			throw new InvalidCursorException("Cursor is malformed.");
		}

		try {
			return new Cursor(
					Instant.parse(payload.substring(0, separator)),
					UUID.fromString(payload.substring(separator + 1)));
		}
		catch (DateTimeParseException | IllegalArgumentException ex) {
			throw new InvalidCursorException("Cursor is malformed.", ex);
		}
	}

	private static String decodeBase64(String cursor) {
		try {
			return new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidCursorException("Cursor is not valid base64url.", ex);
		}
	}

}
