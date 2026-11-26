package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code /ws/presence} end to end: a real servlet container, a real WebSocket handshake and real
 * client sockets speaking the message format the contract documents.
 *
 * <p>Nothing here is mocked, which is the point. The unit tests prove the registry's rules
 * against a hand-written session; only a real client proves that a frame survives Jackson, the
 * handler, the concurrency decorator and Tomcat's buffers on the way out again -- and that the
 * handshake rejects an origin the site does not use.
 *
 * <p>Every assertion waits for a message rather than sleeping and hoping. The server coalesces
 * presence broadcasts to one per page per second, so a count change can legitimately arrive up
 * to a second late; {@link #awaitPresence} is written to tolerate that without making the suite
 * wait a second it does not need.
 *
 * @see PresenceCapacityIntegrationTests for the session ceiling, which needs its own limit
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PresenceIntegrationTests {

	/** The one origin {@code application-test.yml} allows. */
	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private static final String PAGE = "/projects/githmind";

	private static final String OTHER_PAGE = "/guestbook";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@LocalServerPort
	private int port;

	private final List<PresenceTestClient> clients = new ArrayList<>();

	@AfterEach
	void closeClients() {
		this.clients.forEach(PresenceTestClient::close);
		this.clients.clear();
	}

	// --- counting ----------------------------------------------------------------------------

	@Test
	void aSingleVisitorIsToldItIsAloneOnThePage() throws Exception {
		PresenceTestClient visitor = connect();

		visitor.join(PAGE);

		JsonNode presence = visitor.awaitPresence(PAGE);
		assertThat(presence.get("type").asString()).isEqualTo("presence");
		assertThat(presence.get("page").asString()).isEqualTo(PAGE);
		assertThat(presence.get("pageCount").asInt()).isEqualTo(1);
		assertThat(presence.get("siteCount").asInt()).isEqualTo(1);
	}

	@Test
	void twoVisitorsOnThePageSeeACountOfTwo() throws Exception {
		PresenceTestClient first = connect();
		first.join(PAGE);
		first.awaitPresence(PAGE);

		PresenceTestClient second = connect();
		second.join(PAGE);

		// The acceptance criterion: both browsers read two, not just the one that arrived last.
		assertThat(second.awaitPresenceWithCount(PAGE, 2).get("siteCount").asInt()).isEqualTo(2);
		assertThat(first.awaitPresenceWithCount(PAGE, 2).get("pageCount").asInt()).isEqualTo(2);
	}

	@Test
	void visitorsOnDifferentPagesShareASiteCountButNotAPageCount() throws Exception {
		PresenceTestClient here = connect();
		here.join(PAGE);
		here.awaitPresence(PAGE);

		PresenceTestClient there = connect();
		there.join(OTHER_PAGE);

		JsonNode elsewhere = there.awaitPresence(OTHER_PAGE);
		assertThat(elsewhere.get("pageCount").asInt()).isEqualTo(1);
		assertThat(elsewhere.get("siteCount").asInt()).isEqualTo(2);
	}

	@Test
	void navigatingUpdatesTheCountsOnBothPages() throws Exception {
		PresenceTestClient stayer = connect();
		PresenceTestClient mover = connect();
		stayer.join(PAGE);
		mover.join(PAGE);
		stayer.awaitPresenceWithCount(PAGE, 2);
		stayer.clear();

		mover.join(OTHER_PAGE);

		// The page left behind is told it is down to one, and the page joined is told it has one.
		assertThat(stayer.awaitPresenceWithCount(PAGE, 1).get("siteCount").asInt()).isEqualTo(2);
		assertThat(mover.awaitPresenceWithCount(OTHER_PAGE, 1)).isNotNull();
	}

	@Test
	void aDepartureBringsThePageCountBackDown() throws Exception {
		PresenceTestClient stayer = connect();
		PresenceTestClient leaver = connect();
		stayer.join(PAGE);
		leaver.join(PAGE);
		stayer.awaitPresenceWithCount(PAGE, 2);
		stayer.clear();

		leaver.close();

		assertThat(stayer.awaitPresenceWithCount(PAGE, 1).get("siteCount").asInt()).isEqualTo(1);
	}

	@Test
	void aVisitorWhoNeverJoinsIsNotCounted() throws Exception {
		PresenceTestClient lurker = connect();
		PresenceTestClient visitor = connect();

		visitor.join(PAGE);

		// siteCount counts connections that have joined a page, so it equals the sum of the page
		// counts rather than the number of open sockets.
		assertThat(visitor.awaitPresence(PAGE).get("siteCount").asInt()).isEqualTo(1);
		assertThat(lurker.received()).isEmpty();
	}

	// --- bad input ----------------------------------------------------------------------------

	@Test
	void ignoresAnInvalidMessageAndKeepsTheSocketOpen() throws Exception {
		PresenceTestClient visitor = connect();

		visitor.send("not json");
		visitor.send("{\"type\":\"shout\",\"text\":\"hello\"}");
		visitor.send("{}");
		// The socket is still usable, which is what "ignored" has to mean.
		visitor.join(PAGE);

		assertThat(visitor.awaitPresence(PAGE).get("pageCount").asInt()).isEqualTo(1);
		assertThat(visitor.isOpen()).isTrue();
	}

	@Test
	void ignoresACursorMessageAndKeepsTheSocketOpen() throws Exception {
		// Live cursors were dropped after this phase shipped, so "cursor" is now an unknown type
		// like any other. It gets its own test because it is the one unknown type a real client
		// is known to have sent: a tab left open across the deploy keeps sending them, and
		// closing its socket would cost that visitor the count as well.
		PresenceTestClient visitor = connect();
		visitor.join(PAGE);
		visitor.awaitPresence(PAGE);
		visitor.clear();

		visitor.send("{\"type\":\"cursor\",\"x\":0.42,\"y\":0.18}");

		// Nothing is relayed back, to this visitor or anyone else...
		assertThat(visitor.awaitAnyMessage(Duration.ofMillis(750))).isNull();
		// ...and the connection is still open and still counting.
		assertThat(visitor.isOpen()).isTrue();
		visitor.join(OTHER_PAGE);
		assertThat(visitor.awaitPresence(OTHER_PAGE).get("pageCount").asInt()).isEqualTo(1);
	}

	@Test
	void ignoresAJoinForAPageThatIsNotASitePath() throws Exception {
		PresenceTestClient visitor = connect();

		visitor.join("/Projects/GithMind");
		visitor.join("javascript:alert(1)");
		visitor.join("/" + "a".repeat(200));

		assertThat(visitor.awaitAnyMessage(Duration.ofMillis(750))).isNull();
		assertThat(visitor.isOpen()).isTrue();
	}

	// --- the handshake ------------------------------------------------------------------------

	@Test
	void closesAConnectionThatSendsAnOversizedFrame() throws Exception {
		PresenceTestClient visitor = connect();
		visitor.join(PAGE);
		visitor.awaitPresence(PAGE);

		// Well past the 1 KB container limit. Tomcat closes a session that overruns its buffer,
		// which is what makes that limit a limit rather than a suggestion -- and this assertion
		// is also what proves the container bean was actually applied, since the condition on it
		// is evaluated in user configuration.
		visitor.send("{\"type\":\"join\",\"page\":\"/" + "a".repeat(4_000) + "\"}");

		Awaitility.await().atMost(TIMEOUT).until(() -> !visitor.isOpen());
	}

	@Test
	void refusesTheHandshakeFromAnOriginTheSiteDoesNotUse() {
		// A browser protection: Origin is a header any other client can forge, which is why the
		// real limits are the message size and the session ceiling.
		assertThatThrownBy(() -> openSession(new PresenceTestClient(), "https://evil.example.com"))
				.isInstanceOf(ExecutionException.class);
	}

	@Test
	void acceptsTheHandshakeFromTheConfiguredOrigin() throws Exception {
		PresenceTestClient visitor = connect();

		assertThat(visitor.isOpen()).isTrue();
	}

	// --- helpers ------------------------------------------------------------------------------

	/** A connected client, registered for cleanup after the test. */
	private PresenceTestClient connect() throws Exception {
		PresenceTestClient client = new PresenceTestClient();
		client.bind(openSession(client, ALLOWED_ORIGIN));
		this.clients.add(client);
		return client;
	}

	private WebSocketSession openSession(PresenceTestClient client, String origin) throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin(origin);
		return new StandardWebSocketClient()
				.execute(client, headers, URI.create("ws://localhost:" + this.port + "/ws/presence"))
				.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
	}

	/** One client socket, holding every frame the server sent it. */
	private static final class PresenceTestClient extends TextWebSocketHandler {

		private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();

		private final List<String> all = new ArrayList<>();

		private volatile WebSocketSession session;

		void bind(WebSocketSession session) {
			this.session = session;
		}

		@Override
		protected void handleTextMessage(WebSocketSession unused, TextMessage message) {
			String payload = message.getPayload();
			synchronized (this.all) {
				this.all.add(payload);
			}
			this.inbox.add(payload);
		}

		void send(String payload) throws IOException {
			this.session.sendMessage(new TextMessage(payload));
		}

		void join(String page) throws IOException {
			send("{\"type\":\"join\",\"page\":\"" + page + "\"}");
		}

		boolean isOpen() {
			return this.session != null && this.session.isOpen();
		}

		List<String> received() {
			synchronized (this.all) {
				return List.copyOf(this.all);
			}
		}

		/** Forgets what has arrived so far, so a later wait cannot be satisfied by an old frame. */
		void clear() {
			this.inbox.clear();
			synchronized (this.all) {
				this.all.clear();
			}
		}

		/** The next presence message for {@code page}, whatever its counts. */
		JsonNode awaitPresence(String page) throws Exception {
			return awaitMatching(message -> message.has("pageCount")
					&& page.equals(message.get("page").asString()));
		}

		/**
		 * The next presence message for {@code page} reporting exactly {@code pageCount}.
		 *
		 * <p>Skipping past the ones with other counts is deliberate rather than lax: a client
		 * that joins while another is arriving legitimately sees an intermediate count first,
		 * and the broadcast gate can collapse two changes into one message. What the test cares
		 * about is that the right number arrives, not how many did before it.
		 */
		JsonNode awaitPresenceWithCount(String page, int pageCount) throws Exception {
			return awaitMatching(message -> message.has("pageCount")
					&& page.equals(message.get("page").asString())
					&& message.get("pageCount").asInt() == pageCount);
		}

		/** The next frame of any kind, or {@code null} if none arrives in {@code within}. */
		String awaitAnyMessage(Duration within) throws Exception {
			return this.inbox.poll(within.toMillis(), TimeUnit.MILLISECONDS);
		}

		private JsonNode awaitMatching(java.util.function.Predicate<JsonNode> wanted)
				throws Exception {

			long deadline = System.nanoTime() + TIMEOUT.toNanos();
			while (System.nanoTime() < deadline) {
				String payload = this.inbox.poll(
						Math.max(deadline - System.nanoTime(), 0L), TimeUnit.NANOSECONDS);
				if (payload == null) {
					break;
				}
				JsonNode message = MAPPER.readTree(payload);
				if (wanted.test(message)) {
					return message;
				}
			}
			throw new AssertionError("No matching message arrived within " + TIMEOUT
					+ "; received " + received());
		}

		void close() {
			WebSocketSession open = this.session;
			if (open == null) {
				return;
			}
			try {
				open.close(CloseStatus.NORMAL);
			}
			catch (IOException ex) {
				// Already gone; nothing to clean up.
			}
		}

	}

}
