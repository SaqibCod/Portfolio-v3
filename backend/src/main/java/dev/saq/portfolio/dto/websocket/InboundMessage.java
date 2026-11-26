package dev.saq.portfolio.dto.websocket;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Everything a client may send to {@code /ws/presence}, as the contract's "Realtime presence"
 * section defines it. The {@code type} field selects the variant.
 *
 * <p>Sealed, so the handler's switch over an inbound message is exhaustive and a fourth message
 * type cannot be added without the compiler pointing at every place that has to handle it.
 *
 * <p>The three variants are nested rather than split across files because they are not
 * independent types: a permitted subclass of a sealed interface only means anything alongside
 * the interface that permits it.
 *
 * <p>The payload fields are boxed and unvalidated here on purpose. A missing {@code x} would
 * become {@code 0.0} as a primitive -- a cursor in the top-left corner, indistinguishable from a
 * real one -- so absence has to survive parsing as {@code null} and be rejected by
 * {@link dev.saq.portfolio.service.PresenceService}, which owns the rules about what a
 * coordinate or a page may be.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = InboundMessage.JoinMessage.class, name = "join"),
		@JsonSubTypes.Type(value = InboundMessage.PingMessage.class, name = "ping"),
		@JsonSubTypes.Type(value = InboundMessage.CursorMessage.class, name = "cursor") })
public sealed interface InboundMessage {

	/**
	 * Sent on connect and again on every route change.
	 *
	 * @param page the path now being viewed, e.g. {@code /projects/githmind}
	 */
	record JoinMessage(String page) implements InboundMessage {
	}

	/** The 25-second heartbeat. Carries nothing; its arrival is the whole message. */
	record PingMessage() implements InboundMessage {
	}

	/**
	 * An optional cursor position, normalised against the viewport so it means the same thing on
	 * every screen size.
	 *
	 * @param x fraction across, 0..1
	 * @param y fraction down, 0..1
	 */
	record CursorMessage(Double x, Double y) implements InboundMessage {
	}

}
