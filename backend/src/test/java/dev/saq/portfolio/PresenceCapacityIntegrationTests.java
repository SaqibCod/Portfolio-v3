package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * The session ceiling, over a real handshake and with the limit lowered to two.
 *
 * <p>In its own class because the limit has to be a test property, and because proving it
 * against the shipped default would mean opening five hundred and one sockets to assert on the
 * five hundred and second.
 *
 * <p>What is asserted is the shape of the refusal rather than the number: the handshake
 * <em>succeeds</em> and the server then closes the session. That is not a quirk of this
 * implementation but the only option a WebSocket offers -- a handler first runs after the upgrade
 * is complete, so there is no earlier point at which the connection could have been declined.
 *
 * @see PresenceIntegrationTests for everything the endpoint does within its limits
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.presence.max-sessions=2")
class PresenceCapacityIntegrationTests {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	@LocalServerPort
	private int port;

	private final List<WebSocketSession> sessions = new ArrayList<>();

	@AfterEach
	void closeSessions() {
		for (WebSocketSession session : this.sessions) {
			try {
				session.close(CloseStatus.NORMAL);
			}
			catch (Exception ex) {
				// Already closed, which is the expected state for at least one of them.
			}
		}
		this.sessions.clear();
	}

	@Test
	void keepsConnectionsUpToTheCeiling() throws Exception {
		WebSocketSession first = open();
		WebSocketSession second = open();

		assertThat(first.isOpen()).isTrue();
		assertThat(second.isOpen()).isTrue();
	}

	@Test
	void closesTheConnectionThatWouldExceedTheCeiling() throws Exception {
		open();
		open();

		WebSocketSession overflow = open();

		// Closed by the server rather than refused: the close is asynchronous, so this waits for
		// it rather than assuming it has already landed.
		Awaitility.await().atMost(TIMEOUT).until(() -> !overflow.isOpen());
	}

	@Test
	void acceptsAConnectionAgainOnceASlotIsFree() throws Exception {
		WebSocketSession first = open();
		WebSocketSession second = open();
		first.close(CloseStatus.NORMAL);
		// The slot is freed by the server's close callback, which runs after the client's close
		// has travelled, so the replacement handshake has to wait for it. Waiting on the server's
		// own view of the departure rather than retrying handshakes keeps this to one attempt --
		// a retry loop would itself occupy the slot it is testing for.
		Awaitility.await().atMost(TIMEOUT).until(() -> !first.isOpen());

		WebSocketSession replacement = open();

		Awaitility.await().during(Duration.ofMillis(300)).atMost(TIMEOUT)
				.until(() -> replacement.isOpen() && second.isOpen());
	}

	private WebSocketSession open() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin(ALLOWED_ORIGIN);
		WebSocketSession session = new StandardWebSocketClient()
				.execute(new TextWebSocketHandler() {
				}, headers, URI.create("ws://localhost:" + this.port + "/ws/presence"))
				.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
		this.sessions.add(session);
		return session;
	}

}
