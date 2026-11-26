package dev.saq.portfolio.websocket;

import java.io.IOException;

import dev.saq.portfolio.dto.websocket.InboundMessage;
import dev.saq.portfolio.dto.websocket.InboundMessage.JoinMessage;
import dev.saq.portfolio.dto.websocket.InboundMessage.PingMessage;
import dev.saq.portfolio.service.PresenceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The controller layer for {@code /ws/presence}: it parses a frame into an
 * {@link InboundMessage} and hands it to {@link PresenceService}. No rule about what a page may
 * be lives here, the same way a REST controller holds no rules about what a guestbook entry may
 * say.
 *
 * <p>Every session is wrapped in a {@link ConcurrentWebSocketSessionDecorator} before anything
 * else sees it. A visitor can be written to by several threads at once -- a coalesced broadcast
 * firing on a scheduler thread while another visitor's {@code join} is broadcast from its own
 * request thread -- and the underlying JSR-356 session forbids concurrent sends, so without the
 * wrapper those two writes can interleave into one corrupt frame. Wrapping here rather than in
 * the service is what makes that guarantee unconditional: the service never holds a raw session
 * to get it wrong with.
 *
 * <p>Nothing is ever reported back to a client. A WebSocket has no equivalent of a 400, and this
 * endpoint carries no information a visitor needs, so a message that cannot be understood is
 * dropped and the socket stays open. That is also the honest answer to a probe: an unparseable
 * frame tells the sender nothing about what would have parsed.
 */
@Component
public class PresenceWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(PresenceWebSocketHandler.class);

	/**
	 * The largest frame this endpoint will accept, in bytes.
	 *
	 * <p>The biggest legal message is a {@code join} carrying a 101-character page, which is
	 * about 130 bytes. A kilobyte is comfortable headroom and still small enough that a client
	 * cannot make the container buffer anything worth having. Tomcat closes a session that
	 * overruns its buffer, so this is a hard limit rather than a hint.
	 *
	 * <p>Applied per session here rather than globally through a
	 * {@code ServletServerContainerFactoryBean}, which is the usual advice and does not work in
	 * this application. That bean reads the {@code ServerContainer} out of the
	 * {@code ServletContext} as it initialises, so it fails outright in a {@code @SpringBootTest}
	 * that starts no server -- and gating it on one being present cannot work either, because a
	 * condition in user configuration is evaluated before the auto-configuration that would
	 * satisfy it, which silently leaves the limit unset. Setting it on the session is both
	 * narrower, applying only to this endpoint rather than to every endpoint the container has,
	 * and verifiable: {@code StandardWebSocketSession} passes the value straight through to
	 * {@code jakarta.websocket.Session.setMaxTextMessageBufferSize}, and
	 * {@code PresenceIntegrationTests} proves an oversized frame closes the socket.
	 */
	private static final int MAX_TEXT_MESSAGE_BYTES = 1024;

	/**
	 * How long one visitor's writes may be in flight, and how much may queue behind them, before
	 * the decorator gives up on that session. Presence messages are a couple of hundred bytes, so
	 * a visitor who has fallen this far behind is not reading at all.
	 */
	private static final int SEND_TIME_LIMIT_MILLIS = 5_000;

	private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

	private final PresenceService presenceService;

	private final ObjectMapper objectMapper;

	public PresenceWebSocketHandler(PresenceService presenceService, ObjectMapper objectMapper) {
		this.presenceService = presenceService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws IOException {
		session.setTextMessageSizeLimit(MAX_TEXT_MESSAGE_BYTES);

		WebSocketSession concurrencySafe = new ConcurrentWebSocketSessionDecorator(session,
				SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_SIZE_LIMIT_BYTES);

		if (!this.presenceService.register(concurrencySafe)) {
			// The handshake is already complete by the time a handler runs, so a connection over
			// the limit cannot be refused -- only accepted and closed. SERVICE_OVERLOAD says why,
			// which lets a client decide to stop retrying rather than reconnect into the same wall.
			session.close(CloseStatus.SERVICE_OVERLOAD);
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		InboundMessage inbound = parse(session, message);
		if (inbound == null) {
			return;
		}
		String sessionId = session.getId();
		switch (inbound) {
			case JoinMessage join -> this.presenceService.join(sessionId, join.page());
			case PingMessage _ -> this.presenceService.ping(sessionId);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		this.presenceService.unregister(session.getId());
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		// Unregistering is safe to do twice, and doing it here means a half-open socket stops
		// being counted immediately rather than waiting for the stale sweep.
		log.debug("Presence transport error, dropping session: {}", exception.getMessage());
		this.presenceService.unregister(session.getId());
	}

	/**
	 * A frame as an {@link InboundMessage}, or {@code null} if it is not one. Covers an unknown
	 * {@code type}, a missing {@code type}, and anything that is not JSON at all -- Jackson
	 * reports each as an exception rather than as a distinguishable outcome, and the response to
	 * all of them is the same.
	 */
	private InboundMessage parse(WebSocketSession session, TextMessage message) {
		try {
			return this.objectMapper.readValue(message.getPayload(), InboundMessage.class);
		}
		catch (JacksonException ex) {
			log.debug("Ignoring unparseable presence frame on session {}", session.getId());
			return null;
		}
	}

}
