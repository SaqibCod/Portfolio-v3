package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.saq.portfolio.config.PresenceProperties;
import dev.saq.testsupport.MutableClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

/**
 * Every rule the registry owns, with the broadcast gate stubbed to run its work immediately so
 * that what is asserted here is which messages are produced rather than when.
 * {@code PresenceBroadcastServiceTest} covers the timing, and {@code PresenceIntegrationTests}
 * covers both together over a real socket.
 */
class PresenceServiceTest {

	private static final Instant START = Instant.parse("2026-11-25T10:00:00Z");

	private static final String PAGE = "/projects/githmind";

	private static final String OTHER_PAGE = "/guestbook";

	private MutableClock clock;

	private PresenceBroadcastService broadcastService;

	private PresenceService service;

	@BeforeEach
	void setUp() {
		this.clock = new MutableClock(START);
		this.broadcastService = mock(PresenceBroadcastService.class);
		// The gate is not under test here: run every requested broadcast at once, so a count
		// change is observable on the sessions in the same breath as the call that caused it.
		doAnswer(invocation -> {
			invocation.getArgument(1, Runnable.class).run();
			return null;
		}).when(broadcastService).requestBroadcast(anyString(), any(Runnable.class));

		this.service = newService(500, Duration.ofSeconds(60), 20);
	}

	private PresenceService newService(int maxSessions, Duration staleAfter, int cursorRate) {
		return new PresenceService(
				new PresenceProperties(maxSessions, staleAfter, cursorRate),
				this.broadcastService, new ObjectMapper(), this.clock);
	}

	// --- registering -------------------------------------------------------------------------

	@Test
	void acceptsAConnectionAndSaysNothingUntilItJoins() {
		RecordingSession session = new RecordingSession("s1");

		assertThat(service.register(session)).isTrue();

		// No page yet, so no page's counts have changed and there is nothing to send.
		assertThat(session.sent()).isEmpty();
		assertThat(service.siteCount()).isZero();
	}

	@Test
	void refusesAConnectionPastTheSessionCeiling() {
		PresenceService limited = newService(2, Duration.ofSeconds(60), 20);

		assertThat(limited.register(new RecordingSession("s1"))).isTrue();
		assertThat(limited.register(new RecordingSession("s2"))).isTrue();
		assertThat(limited.register(new RecordingSession("s3"))).isFalse();
	}

	@Test
	void freesTheSlotADepartedConnectionHeld() {
		PresenceService limited = newService(1, Duration.ofSeconds(60), 20);
		RecordingSession first = new RecordingSession("s1");
		limited.register(first);

		assertThat(limited.register(new RecordingSession("s2"))).isFalse();
		limited.unregister(first.getId());

		assertThat(limited.register(new RecordingSession("s3"))).isTrue();
	}

	@Test
	void givesEveryConnectionTheContractsAnonymousIdShape() {
		List<RecordingSession> sessions = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			RecordingSession session = new RecordingSession("s" + i);
			sessions.add(session);
			service.register(session);
			service.join(session.getId(), PAGE);
		}

		// Read the ids off the cursor relay, which is the only place they are published. The
		// joins above have already sent presence counts, so clear first to be sure of what is read.
		sessions.forEach(RecordingSession::clear);
		service.cursor(sessions.getFirst().getId(), 0.5d, 0.5d);

		// Twenty-five ids out of 65,536, so this also stands in for the collision retry: every
		// peer receives exactly one relay, and no two connections are sharing an id.
		assertThat(sessions.subList(1, sessions.size()))
				.allSatisfy(peer -> assertThat(peer.sent())
						.singleElement(as(STRING))
						.containsPattern("\"id\":\"anon-[0-9a-f]{4}\""));
	}

	// --- joining ----------------------------------------------------------------------------

	@Test
	void countsTwoViewersOfTheSamePage() {
		RecordingSession first = join("s1", PAGE);
		RecordingSession second = join("s2", PAGE);

		assertThat(service.pageCount(PAGE)).isEqualTo(2);
		assertThat(service.siteCount()).isEqualTo(2);
		// The second join tells both of them, not only the one that sent it.
		assertThat(first.sent().getLast()).contains("\"pageCount\":2").contains("\"siteCount\":2");
		assertThat(second.sent().getLast()).contains("\"pageCount\":2");
	}

	@Test
	void countsPagesSeparatelyButTheSiteAsAWhole() {
		join("s1", PAGE);
		join("s2", OTHER_PAGE);
		join("s3", OTHER_PAGE);

		assertThat(service.pageCount(PAGE)).isEqualTo(1);
		assertThat(service.pageCount(OTHER_PAGE)).isEqualTo(2);
		assertThat(service.siteCount()).isEqualTo(3);
	}

	@Test
	void movingPagesUpdatesBothTheOldAndTheNewPage() {
		RecordingSession stayer = join("s1", PAGE);
		RecordingSession mover = join("s2", PAGE);
		stayer.clear();

		service.join(mover.getId(), OTHER_PAGE);

		assertThat(service.pageCount(PAGE)).isEqualTo(1);
		assertThat(service.pageCount(OTHER_PAGE)).isEqualTo(1);
		// The page left behind is told it is down to one.
		assertThat(stayer.sent()).anySatisfy(message -> assertThat(message)
				.contains("\"page\":\"" + PAGE + "\"")
				.contains("\"pageCount\":1"));
		assertThat(mover.sent().getLast()).contains("\"page\":\"" + OTHER_PAGE + "\"");
	}

	@Test
	void answersAReJoinOfThePageAlreadyBeingViewed() {
		RecordingSession session = join("s1", PAGE);
		session.clear();

		// A reconnecting client re-sends join and is waiting for counts; silence would leave it
		// with no number to show.
		service.join(session.getId(), PAGE);

		assertThat(session.sent()).hasSize(1);
		assertThat(service.pageCount(PAGE)).isEqualTo(1);
	}

	@Test
	void ignoresAPageOutsideTheAllowedPattern() {
		RecordingSession session = new RecordingSession("s1");
		service.register(session);

		service.join(session.getId(), "/Projects/GithMind");
		service.join(session.getId(), "no-leading-slash");
		service.join(session.getId(), "/projects?tab=1");
		service.join(session.getId(), "/projects/<script>");
		service.join(session.getId(), "/" + "a".repeat(101));
		service.join(session.getId(), null);

		assertThat(service.siteCount()).isZero();
		assertThat(session.sent()).isEmpty();
	}

	@Test
	void acceptsTheSitesOwnPaths() {
		assertThat(join("s1", "/").sent()).isNotEmpty();
		assertThat(join("s2", "/projects/githmind").sent()).isNotEmpty();
		assertThat(join("s3", "/projects/medi-scan").sent()).isNotEmpty();
		assertThat(join("s4", "/some_page").sent()).isNotEmpty();
	}

	@Test
	void ignoresAJoinFromAnUnknownSession() {
		service.join("never-registered", PAGE);

		assertThat(service.siteCount()).isZero();
		verify(broadcastService, never()).requestBroadcast(anyString(), any(Runnable.class));
	}

	// --- leaving ----------------------------------------------------------------------------

	@Test
	void tellsThePageWhenAViewerLeaves() {
		RecordingSession stayer = join("s1", PAGE);
		RecordingSession leaver = join("s2", PAGE);
		stayer.clear();

		service.unregister(leaver.getId());

		assertThat(service.pageCount(PAGE)).isEqualTo(1);
		assertThat(stayer.sent().getLast()).contains("\"pageCount\":1").contains("\"siteCount\":1");
	}

	@Test
	void unregisteringTwiceBroadcastsOnce() {
		// A transport error and a close callback can both report the same departure.
		RecordingSession stayer = join("s1", PAGE);
		RecordingSession leaver = join("s2", PAGE);
		stayer.clear();

		service.unregister(leaver.getId());
		service.unregister(leaver.getId());

		assertThat(stayer.sent()).hasSize(1);
	}

	// --- cursors ----------------------------------------------------------------------------

	@Test
	void relaysACursorToTheOtherViewersOfThePage() {
		RecordingSession sender = join("s1", PAGE);
		RecordingSession peer = join("s2", PAGE);
		peer.clear();

		service.cursor(sender.getId(), 0.42d, 0.18d);

		assertThat(peer.sent()).hasSize(1);
		assertThat(peer.sent().getFirst())
				.contains("\"type\":\"cursor\"")
				.contains("\"x\":0.42")
				.contains("\"y\":0.18");
	}

	@Test
	void neverEchoesACursorBackToItsSender() {
		RecordingSession sender = join("s1", PAGE);
		join("s2", PAGE);
		sender.clear();

		service.cursor(sender.getId(), 0.5d, 0.5d);

		assertThat(sender.sent()).isEmpty();
	}

	@Test
	void doesNotRelayACursorAcrossPages() {
		RecordingSession sender = join("s1", PAGE);
		RecordingSession elsewhere = join("s2", OTHER_PAGE);
		elsewhere.clear();

		service.cursor(sender.getId(), 0.5d, 0.5d);

		assertThat(elsewhere.sent()).isEmpty();
	}

	@Test
	void dropsCoordinatesOutsideTheUnitSquare() {
		RecordingSession sender = join("s1", PAGE);
		RecordingSession peer = join("s2", PAGE);
		peer.clear();

		service.cursor(sender.getId(), -0.01d, 0.5d);
		service.cursor(sender.getId(), 1.01d, 0.5d);
		service.cursor(sender.getId(), 0.5d, Double.NaN);
		service.cursor(sender.getId(), 0.5d, Double.POSITIVE_INFINITY);
		// Absent rather than wrong: a missing x must not become a cursor at the left edge.
		service.cursor(sender.getId(), null, 0.5d);
		service.cursor(sender.getId(), 0.5d, null);

		assertThat(peer.sent()).isEmpty();
	}

	@Test
	void keepsTheEdgesOfTheUnitSquare() {
		RecordingSession sender = join("s1", PAGE);
		RecordingSession peer = join("s2", PAGE);
		peer.clear();

		service.cursor(sender.getId(), 0.0d, 0.0d);
		service.cursor(sender.getId(), 1.0d, 1.0d);

		assertThat(peer.sent()).hasSize(2);
	}

	@Test
	void ignoresACursorFromAConnectionThatHasNotJoined() {
		RecordingSession peer = join("s1", PAGE);
		RecordingSession silent = new RecordingSession("s2");
		service.register(silent);
		peer.clear();

		service.cursor(silent.getId(), 0.5d, 0.5d);

		assertThat(peer.sent()).isEmpty();
	}

	@Test
	void dropsCursorsPastTheBudgetForThatSecond() {
		PresenceService limited = newService(500, Duration.ofSeconds(60), 3);
		RecordingSession sender = new RecordingSession("s1");
		RecordingSession peer = new RecordingSession("s2");
		limited.register(sender);
		limited.register(peer);
		limited.join(sender.getId(), PAGE);
		limited.join(peer.getId(), PAGE);
		peer.clear();

		for (int i = 0; i < 10; i++) {
			limited.cursor(sender.getId(), 0.5d, 0.5d);
		}

		assertThat(peer.sent()).hasSize(3);
	}

	@Test
	void refillsTheCursorBudgetEachSecond() {
		PresenceService limited = newService(500, Duration.ofSeconds(60), 2);
		RecordingSession sender = new RecordingSession("s1");
		RecordingSession peer = new RecordingSession("s2");
		limited.register(sender);
		limited.register(peer);
		limited.join(sender.getId(), PAGE);
		limited.join(peer.getId(), PAGE);
		peer.clear();

		limited.cursor(sender.getId(), 0.5d, 0.5d);
		limited.cursor(sender.getId(), 0.5d, 0.5d);
		limited.cursor(sender.getId(), 0.5d, 0.5d);
		clock.advance(Duration.ofSeconds(1));
		limited.cursor(sender.getId(), 0.5d, 0.5d);

		assertThat(peer.sent()).hasSize(3);
	}

	@Test
	void budgetsEachConnectionSeparately() {
		PresenceService limited = newService(500, Duration.ofSeconds(60), 1);
		RecordingSession first = new RecordingSession("s1");
		RecordingSession second = new RecordingSession("s2");
		RecordingSession peer = new RecordingSession("s3");
		for (RecordingSession session : List.of(first, second, peer)) {
			limited.register(session);
			limited.join(session.getId(), PAGE);
		}
		peer.clear();

		// One busy visitor must not spend anyone else's budget.
		limited.cursor(first.getId(), 0.1d, 0.1d);
		limited.cursor(first.getId(), 0.2d, 0.2d);
		limited.cursor(second.getId(), 0.3d, 0.3d);

		assertThat(peer.sent()).hasSize(2);
	}

	// --- the stale sweep ----------------------------------------------------------------------

	@Test
	void closesAConnectionThatHasStoppedBeating() {
		RecordingSession quiet = join("s1", PAGE);

		clock.advance(Duration.ofSeconds(61));

		assertThat(service.closeStaleSessions()).isEqualTo(1);
		assertThat(quiet.closedWith()).isEqualTo(CloseStatus.GOING_AWAY);
		assertThat(service.siteCount()).isZero();
	}

	@Test
	void leavesAConnectionThatIsStillBeatingAlone() {
		RecordingSession healthy = join("s1", PAGE);

		clock.advance(Duration.ofSeconds(40));
		service.ping(healthy.getId());
		clock.advance(Duration.ofSeconds(40));

		assertThat(service.closeStaleSessions()).isZero();
		assertThat(healthy.closedWith()).isNull();
		assertThat(service.siteCount()).isEqualTo(1);
	}

	@Test
	void sweepingTellsThePageItsCountDropped() {
		RecordingSession stayer = join("s1", PAGE);
		RecordingSession quiet = join("s2", PAGE);
		clock.advance(Duration.ofSeconds(30));
		service.ping(stayer.getId());
		clock.advance(Duration.ofSeconds(31));
		stayer.clear();

		service.closeStaleSessions();

		assertThat(quiet.closedWith()).isEqualTo(CloseStatus.GOING_AWAY);
		assertThat(stayer.sent().getLast()).contains("\"pageCount\":1");
	}

	@Test
	void freesTheSlotEvenWhenClosingTheSocketFails() {
		// The usual reason a session went stale is that it is already gone, so close() throwing
		// must not leave the registry holding it.
		RecordingSession broken = new RecordingSession("s1");
		broken.failOnClose();
		service.register(broken);
		service.join(broken.getId(), PAGE);

		clock.advance(Duration.ofSeconds(61));

		assertThat(service.closeStaleSessions()).isEqualTo(1);
		assertThat(service.sessionCount()).isZero();
	}

	@Test
	void aConnectionThatNeverJoinedStillGoesStale() {
		RecordingSession session = new RecordingSession("s1");
		service.register(session);

		clock.advance(Duration.ofSeconds(61));

		assertThat(service.closeStaleSessions()).isEqualTo(1);
		assertThat(service.sessionCount()).isZero();
	}

	// --- broadcasting -------------------------------------------------------------------------

	@Test
	void sendsNothingForAPageNobodyIsViewing() {
		join("s1", PAGE);

		service.broadcastPresence("/nobody-here");

		// Asserting on the absence of a throw as much as on the absence of a send.
		assertThat(service.pageCount("/nobody-here")).isZero();
	}

	@Test
	void aFailedSendDoesNotStopTheRestOfTheBroadcast() {
		RecordingSession broken = new RecordingSession("s1");
		broken.failOnSend();
		service.register(broken);
		service.join(broken.getId(), PAGE);
		RecordingSession healthy = join("s2", PAGE);
		healthy.clear();

		service.broadcastPresence(PAGE);

		assertThat(healthy.sent()).hasSize(1);
	}

	@Test
	void sendsTheWireShapeTheContractDocuments() {
		RecordingSession session = join("s1", PAGE);

		assertThat(session.sent().getFirst()).isEqualTo(
				"{\"type\":\"presence\",\"page\":\"" + PAGE + "\",\"pageCount\":1,\"siteCount\":1}");
	}

	// --- helpers ------------------------------------------------------------------------------

	private RecordingSession join(String id, String page) {
		RecordingSession session = new RecordingSession(id);
		service.register(session);
		service.join(session.getId(), page);
		return session;
	}

	/**
	 * A session that keeps what was written to it. Mockito could do this, but a handful of
	 * sessions per test each needing a payload captor reads far worse than a class that just
	 * holds a list.
	 */
	private static final class RecordingSession implements WebSocketSession {

		private final String id;

		private final List<String> sent = new ArrayList<>();

		private CloseStatus closedWith;

		private boolean failSend;

		private boolean failClose;

		private RecordingSession(String id) {
			this.id = id;
		}

		List<String> sent() {
			return List.copyOf(this.sent);
		}

		void clear() {
			this.sent.clear();
		}

		CloseStatus closedWith() {
			return this.closedWith;
		}

		void failOnSend() {
			this.failSend = true;
		}

		void failOnClose() {
			this.failClose = true;
		}

		@Override
		public String getId() {
			return this.id;
		}

		@Override
		public void sendMessage(WebSocketMessage<?> message) throws IOException {
			if (this.failSend) {
				throw new IOException("socket closed");
			}
			this.sent.add(((TextMessage) message).getPayload());
		}

		@Override
		public void close() throws IOException {
			close(CloseStatus.NORMAL);
		}

		@Override
		public void close(CloseStatus status) throws IOException {
			if (this.failClose) {
				throw new IOException("already gone");
			}
			this.closedWith = status;
		}

		@Override
		public boolean isOpen() {
			return this.closedWith == null;
		}

		// Nothing below is reachable from PresenceService; it exists because the interface is
		// wider than the three methods the service uses.

		@Override
		public java.net.URI getUri() {
			return null;
		}

		@Override
		public org.springframework.http.HttpHeaders getHandshakeHeaders() {
			return org.springframework.http.HttpHeaders.EMPTY;
		}

		@Override
		public java.util.Map<String, Object> getAttributes() {
			return java.util.Map.of();
		}

		@Override
		public java.security.Principal getPrincipal() {
			return null;
		}

		@Override
		public java.net.InetSocketAddress getLocalAddress() {
			return null;
		}

		@Override
		public java.net.InetSocketAddress getRemoteAddress() {
			return null;
		}

		@Override
		public String getAcceptedProtocol() {
			return null;
		}

		@Override
		public void setTextMessageSizeLimit(int messageSizeLimit) {
		}

		@Override
		public int getTextMessageSizeLimit() {
			return 0;
		}

		@Override
		public void setBinaryMessageSizeLimit(int messageSizeLimit) {
		}

		@Override
		public int getBinaryMessageSizeLimit() {
			return 0;
		}

		@Override
		public java.util.List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
			return java.util.List.of();
		}

	}

}
