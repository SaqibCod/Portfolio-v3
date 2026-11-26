package dev.saq.portfolio.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.saq.portfolio.service.PresenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.databind.ObjectMapper;

/**
 * Parsing and delegation, which is everything this layer does. What the service makes of a page
 * is {@code PresenceServiceTest}'s business.
 */
class PresenceWebSocketHandlerTest {

	private static final String SESSION_ID = "session-1";

	private PresenceService presenceService;

	private WebSocketSession session;

	private PresenceWebSocketHandler handler;

	@BeforeEach
	void setUp() {
		this.presenceService = mock(PresenceService.class);
		when(presenceService.register(any())).thenReturn(true);
		this.session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn(SESSION_ID);
		this.handler = new PresenceWebSocketHandler(this.presenceService, new ObjectMapper());
	}

	@Test
	void wrapsEverySessionForConcurrentSendsBeforeTheServiceSeesIt() throws Exception {
		// The service never holds a raw session, so two threads writing to the same visitor
		// cannot interleave into one corrupt frame.
		handler.afterConnectionEstablished(session);

		ArgumentCaptor<WebSocketSession> registered = ArgumentCaptor.forClass(WebSocketSession.class);
		verify(presenceService).register(registered.capture());
		assertThat(registered.getValue()).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
	}

	@Test
	void closesAConnectionTheRegistryWouldNotTake() throws Exception {
		when(presenceService.register(any())).thenReturn(false);

		handler.afterConnectionEstablished(session);

		// SERVICE_OVERLOAD rather than a silent drop, so a client can tell this apart from a
		// network failure and stop reconnecting into the same wall.
		verify(session).close(CloseStatus.SERVICE_OVERLOAD);
	}

	@Test
	void leavesAnAcceptedConnectionOpen() throws Exception {
		handler.afterConnectionEstablished(session);

		verify(session, never()).close(any());
	}

	@Test
	void readsAJoin() {
		handle("{\"type\":\"join\",\"page\":\"/projects/githmind\"}");

		verify(presenceService).join(SESSION_ID, "/projects/githmind");
	}

	@Test
	void readsAPing() {
		handle("{\"type\":\"ping\"}");

		verify(presenceService).ping(SESSION_ID);
	}

	@Test
	void ignoresACursorMessageAndKeepsTheConnectionOpen() throws Exception {
		// Live cursors were dropped, so "cursor" is now just another unknown type. A client
		// still sending them — an old tab, or a stale deploy — must not be disconnected for it.
		handle("{\"type\":\"cursor\",\"x\":0.42,\"y\":0.18}");

		verifyNoInteractions(presenceService);
		verify(session, never()).close(any());
	}

	@Test
	void ignoresAnUnknownMessageType() {
		handle("{\"type\":\"shout\",\"text\":\"hello\"}");

		verifyNoInteractions(presenceService);
	}

	@Test
	void ignoresAMessageWithNoType() {
		handle("{\"page\":\"/\"}");

		verifyNoInteractions(presenceService);
	}

	@Test
	void ignoresSomethingThatIsNotJsonAtAll() {
		handle("not json");
		handle("");
		handle("[]");
		handle("{\"type\":\"join\",\"page\":");

		verifyNoInteractions(presenceService);
	}

	@Test
	void ignoresAJoinWhosePageIsTheWrongType() {
		handle("{\"type\":\"join\",\"page\":{\"nested\":true}}");

		verifyNoInteractions(presenceService);
	}

	@Test
	void anUnparseableFrameLeavesTheSocketOpen() throws Exception {
		// A WebSocket has no equivalent of a 400, and this endpoint carries nothing a visitor
		// needs, so the frame is dropped rather than the connection.
		handle("not json");

		verify(session, never()).close(any());
	}

	@Test
	void dropsAConnectionWhenItCloses() {
		handler.afterConnectionClosed(session, CloseStatus.NORMAL);

		verify(presenceService).unregister(SESSION_ID);
	}

	@Test
	void dropsAConnectionOnATransportError() {
		// Doing it here too means a half-open socket stops being counted immediately rather
		// than waiting for the stale sweep; unregister is safe to call twice.
		handler.handleTransportError(session, new RuntimeException("reset by peer"));

		verify(presenceService).unregister(SESSION_ID);
	}

	@Test
	void neverWritesToASocketItself() throws Exception {
		handle("{\"type\":\"join\",\"page\":\"/\"}");
		handle("{\"type\":\"ping\"}");

		// Sending belongs to the service, which is the only thing holding the registry a
		// broadcast needs. The handler parses and delegates, and nothing more.
		verify(session, never()).sendMessage(any());
		verify(presenceService).join(SESSION_ID, "/");
	}

	/** Runs one frame through the handler, failing the test if it throws. */
	private void handle(String payload) {
		try {
			handler.handleTextMessage(session, new TextMessage(payload));
		}
		catch (Exception ex) {
			throw new AssertionError("Handler threw on payload: " + payload, ex);
		}
	}

}
