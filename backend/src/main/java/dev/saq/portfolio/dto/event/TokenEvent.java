package dev.saq.portfolio.dto.event;

/**
 * The payload of an SSE {@code token} event: one fragment of the answer as the model produces it.
 *
 * <p>An object rather than a bare string, which costs a few bytes per token and buys two things:
 * the stream stays valid JSON per event (so leading spaces and newlines survive without quoting
 * rules of their own), and a later phase can add a field without changing the event's shape.
 *
 * @param text the fragment, appended verbatim to what the visitor is reading. Never trimmed —
 *             the space between two words often arrives as its own token
 */
public record TokenEvent(String text) {

}
