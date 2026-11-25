package dev.saq.portfolio.dto.websocket;

/**
 * One visitor's cursor, relayed to the others on the same page.
 *
 * <p>{@code id} is the random per-connection id from {@link dev.saq.portfolio.model.Presence},
 * never anything derived from the visitor: it exists only so a receiving client can tell two
 * simultaneous cursors apart, and it stops meaning anything the moment the socket closes.
 *
 * @param id the sender's anonymous connection id, e.g. {@code anon-7f3a}
 * @param x  fraction across the viewport, 0..1
 * @param y  fraction down the viewport, 0..1
 */
public record CursorBroadcastMessage(String type, String id, double x, double y) {

	private static final String TYPE = "cursor";

	/** The only way to build one, so {@code type} can never be spelled wrongly. */
	public static CursorBroadcastMessage of(String id, double x, double y) {
		return new CursorBroadcastMessage(TYPE, id, x, y);
	}

}
