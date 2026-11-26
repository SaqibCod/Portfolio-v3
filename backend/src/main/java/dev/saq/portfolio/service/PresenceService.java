package dev.saq.portfolio.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import dev.saq.portfolio.config.PresenceProperties;
import dev.saq.portfolio.dto.websocket.PresenceMessage;
import dev.saq.portfolio.model.Presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The live connection registry and every rule about what a connection may do.
 *
 * <p>State is per instance and held only in memory, which is the whole design: presence is true
 * while a socket is open and false afterwards, so there is nothing here worth a table. The cost
 * is that it does not survive a restart (visitors reconnect, so it heals in seconds) and does
 * not span instances. Scaling past one backend means replacing this map with a shared one --
 * publishing joins and leaves over Redis pub/sub and keeping only the local sessions here --
 * which phase 09 deliberately leaves out of scope.
 *
 * <p>Nothing identifying is stored. A connection is a container-assigned session id, a random
 * public id, a path and a timestamp; there is no address, no hash of one, and no cookie.
 */
@Service
public class PresenceService {

	private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

	/**
	 * What may be joined. Deliberately narrower than "any URL": these are the site's own route
	 * paths, which are lowercase and carry no query string, and the value is echoed back to every
	 * other viewer of that page. Anchoring the pattern and excluding everything else is what
	 * stops a client making the server repeat arbitrary text to other people.
	 */
	private static final Pattern PAGE_PATTERN = Pattern.compile("^/[a-z0-9/_-]{0,100}$");

	/** {@code anon-} plus four hex characters. */
	private static final int ID_SPACE = 1 << 16;

	/**
	 * Four hex digits is 65,536 ids, so among a few hundred live connections a collision is
	 * likely rather than rare. Regenerating on a clash costs nothing; the bound just stops the
	 * loop if the registry is somehow almost full of ids.
	 */
	private static final int ID_ATTEMPTS = 64;

	private final PresenceProperties properties;

	private final PresenceBroadcastService broadcastService;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	/** Keyed by the container's session id, which is never sent to a client. */
	private final ConcurrentHashMap<String, Presence> sessions = new ConcurrentHashMap<>();

	@Autowired
	public PresenceService(PresenceProperties properties, PresenceBroadcastService broadcastService,
			ObjectMapper objectMapper) {

		this(properties, broadcastService, objectMapper, Clock.systemUTC());
	}

	/** Package-private so a test can pin time instead of sleeping. */
	PresenceService(PresenceProperties properties, PresenceBroadcastService broadcastService,
			ObjectMapper objectMapper, Clock clock) {

		this.properties = properties;
		this.broadcastService = broadcastService;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/**
	 * Takes a newly connected session into the registry.
	 *
	 * <p>No broadcast happens here: the connection has no page until its {@code join} arrives, so
	 * there is nothing yet whose count has changed.
	 *
	 * @return {@code false} when the registry is full, which means the caller must close the
	 *         session. The check reads the current size, so simultaneous handshakes can overshoot
	 *         the limit by a connection or two; the point is a bound on memory, not an exact
	 *         headcount
	 */
	public boolean register(WebSocketSession session) {
		if (this.sessions.size() >= this.properties.maxSessions()) {
			log.warn("Refusing presence connection: {} sessions already open", this.sessions.size());
			return false;
		}
		this.sessions.put(session.getId(), new Presence(nextId(), null, this.clock.instant(), session));
		return true;
	}

	/**
	 * Drops a connection and tells its page. Safe to call more than once for the same session,
	 * which matters because a transport error and a close can both report the same departure.
	 */
	public void unregister(String sessionId) {
		Presence removed = this.sessions.remove(sessionId);
		if (removed != null && removed.hasJoined()) {
			requestBroadcast(removed.page());
		}
	}

	/**
	 * Moves a connection onto {@code page}, telling both the page it left and the page it joined.
	 * An unknown session, or a page outside {@link #PAGE_PATTERN}, is ignored.
	 */
	public void join(String sessionId, String page) {
		if (page == null || !PAGE_PATTERN.matcher(page).matches()) {
			log.debug("Ignoring join for session {}: page not allowed", sessionId);
			return;
		}
		Instant now = this.clock.instant();
		// The holder captures what this connection was viewing before the move. Reading it
		// inside computeIfPresent is what makes "read the old page and write the new one" a
		// single atomic step, so two joins racing cannot both report the same page left behind.
		Presence[] previous = new Presence[1];
		this.sessions.computeIfPresent(sessionId, (unused, current) -> {
			previous[0] = current;
			return current.onPage(page, now);
		});
		if (previous[0] == null) {
			return;
		}
		// The joined page always answers, even when it is the page this connection was already
		// on, so a client that re-sends join receives the counts it is waiting for.
		requestBroadcast(page);
		if (previous[0].hasJoined() && !page.equals(previous[0].page())) {
			requestBroadcast(previous[0].page());
		}
	}

	/** Marks a connection as alive, which is all that keeps the stale sweeper off it. */
	public void ping(String sessionId) {
		Instant now = this.clock.instant();
		this.sessions.computeIfPresent(sessionId, (unused, current) -> current.seenAt(now));
	}

	/**
	 * Closes every connection not heard from within {@code app.presence.stale-after}.
	 *
	 * <p>Closing is what the client's reconnect is for: a browser that is awake and merely
	 * blocked gets a fresh socket within a second, whereas a socket the container has not noticed
	 * is gone would be counted as a viewer forever. The registry entry goes here rather than
	 * waiting for the close callback, so a close that fails still frees the slot.
	 *
	 * @return how many were closed
	 */
	public int closeStaleSessions() {
		Instant cutoff = this.clock.instant().minus(this.properties.staleAfter());
		int closed = 0;
		for (Presence presence : List.copyOf(this.sessions.values())) {
			if (!presence.isStaleAt(cutoff)) {
				continue;
			}
			unregister(presence.session().getId());
			closed++;
			try {
				presence.session().close(CloseStatus.GOING_AWAY);
			}
			catch (IOException | RuntimeException ex) {
				// Already gone, which is the usual reason a session went stale in the first place.
				log.debug("Could not close stale presence session: {}", ex.getMessage());
			}
		}
		return closed;
	}

	/** How many connections are viewing {@code page}. */
	public int pageCount(String page) {
		return viewersOf(page).size();
	}

	/**
	 * How many connections are on the site.
	 *
	 * <p>Counts only connections that have joined a page, so this always equals the sum of the
	 * page counts. A socket that has finished its handshake but whose {@code join} is still in
	 * flight is a visitor nobody can place yet, and counting it would make the site total briefly
	 * disagree with every page total for no gain.
	 */
	public int siteCount() {
		return (int) this.sessions.values().stream().filter(Presence::hasJoined).count();
	}

	/** Live connections including those still to join. Used by the capacity check and by tests. */
	int sessionCount() {
		return this.sessions.size();
	}

	/**
	 * Sends the current counts for {@code page} to everyone viewing it. Called through
	 * {@link PresenceBroadcastService}, possibly a moment after the change that prompted it,
	 * which is why it reads the counts here rather than being handed them.
	 */
	void broadcastPresence(String page) {
		List<Presence> viewers = viewersOf(page);
		if (viewers.isEmpty()) {
			return;
		}
		String payload = serialize(PresenceMessage.of(page, viewers.size(), siteCount()));
		if (payload == null) {
			return;
		}
		for (Presence viewer : viewers) {
			send(viewer, payload);
		}
	}

	private void requestBroadcast(String page) {
		this.broadcastService.requestBroadcast(page, () -> broadcastPresence(page));
	}

	private List<Presence> viewersOf(String page) {
		return this.sessions.values().stream()
				.filter(presence -> page.equals(presence.page()))
				.toList();
	}

	/** {@code anon-} plus four hex digits, retried until it is not already in use. */
	private String nextId() {
		for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
			String candidate = randomId();
			if (!isIdInUse(candidate)) {
				return candidate;
			}
		}
		// Every attempt collided, which needs tens of thousands of live connections. The id is
		// only a label in the log, so a duplicate is far cheaper than refusing the connection.
		return randomId();
	}

	private String randomId() {
		return "anon-%04x".formatted(this.random.nextInt(ID_SPACE));
	}

	private boolean isIdInUse(String candidate) {
		return this.sessions.values().stream().anyMatch(presence -> presence.id().equals(candidate));
	}

	private String serialize(Object message) {
		try {
			return this.objectMapper.writeValueAsString(message);
		}
		catch (JacksonException ex) {
			// Records of strings and doubles; reaching this would mean a programming error.
			log.warn("Could not serialize presence message: {}", ex.getMessage());
			return null;
		}
	}

	/**
	 * Writes one frame. The session is already wrapped for concurrent sends by the handler, so
	 * two broadcasts to the same visitor cannot interleave.
	 */
	private void send(Presence recipient, String payload) {
		try {
			recipient.session().sendMessage(new TextMessage(payload));
		}
		catch (IOException | RuntimeException ex) {
			// A failed send means the socket is going or gone. The sweeper and the container's
			// close callback both clean up after it; there is nothing to tell the visitor.
			log.debug("Could not send to presence session {}: {}", recipient.id(), ex.getMessage());
		}
	}

}
