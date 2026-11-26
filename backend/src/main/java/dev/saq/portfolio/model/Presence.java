package dev.saq.portfolio.model;

import java.time.Instant;

import org.springframework.web.socket.WebSocketSession;

/**
 * One live connection, held in memory only.
 *
 * <p>Unlike everything else in this package this is not a database entity and there is no
 * repository for it: presence is true for as long as a socket is open and meaningless
 * afterwards, so persisting it would only ever produce rows describing visitors who have
 * already left.
 *
 * <p>There is no identifier here beyond the container's own session, which the registry keys on
 * and which never leaves the server. A connection carried a random {@code anon-xxxx} label while
 * live cursors existed, because a receiving client had to tell two cursors apart; with cursors
 * gone nothing published it and nothing read it, so it was removed rather than left to look like
 * state something depends on.
 *
 * @param page     the path this connection is viewing, or {@code null} between the handshake and
 *                 the first {@code join}
 * @param lastSeen when the last message arrived; the stale sweeper reads this
 * @param session  the sink to write to, already wrapped for concurrent sends by the handler
 */
public record Presence(String page, Instant lastSeen, WebSocketSession session) {

	/** The same connection, now viewing {@code page}. */
	public Presence onPage(String page, Instant at) {
		return new Presence(page, at, this.session);
	}

	/** The same connection, heard from at {@code at}. */
	public Presence seenAt(Instant at) {
		return new Presence(this.page, at, this.session);
	}

	/** True once a {@code join} has arrived, which is what makes this connection countable. */
	public boolean hasJoined() {
		return this.page != null;
	}

	/** True when nothing has arrived on this connection since {@code cutoff}. */
	public boolean isStaleAt(Instant cutoff) {
		return this.lastSeen.isBefore(cutoff);
	}

}
