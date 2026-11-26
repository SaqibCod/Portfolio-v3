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
 * <p>The two variants are nested rather than split across files because they are not independent
 * types: a permitted subclass of a sealed interface only means anything alongside the interface
 * that permits it.
 *
 * <p>{@code page} is left unvalidated here on purpose. What a page may be is a rule, and rules
 * belong to {@link dev.saq.portfolio.service.PresenceService} rather than to the shape a frame
 * arrives in.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = InboundMessage.JoinMessage.class, name = "join"),
		@JsonSubTypes.Type(value = InboundMessage.PingMessage.class, name = "ping") })
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

}
