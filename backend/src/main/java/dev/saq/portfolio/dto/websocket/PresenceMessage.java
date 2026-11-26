package dev.saq.portfolio.dto.websocket;

/**
 * Sent to every viewer of a page whenever its counts change.
 *
 * <p>{@code type} is a field rather than an annotation so that the wire shape the contract
 * documents is visible in the record itself, and so a test can assert on it.
 *
 * @param page      the path these counts describe
 * @param pageCount how many connections are viewing that page
 * @param siteCount how many connections are on the site altogether
 */
public record PresenceMessage(String type, String page, int pageCount, int siteCount) {

	private static final String TYPE = "presence";

	/** The only way to build one, so {@code type} can never be spelled wrongly. */
	public static PresenceMessage of(String page, int pageCount, int siteCount) {
		return new PresenceMessage(TYPE, page, pageCount, siteCount);
	}

}
