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
 * <p>{@code id} is a random per-connection label ({@code anon-7f3a}) used only in logs, so one
 * connection's joins, leaves and failed sends can be followed without recording anything about
 * who it belongs to. It left the wire with live cursors, which was the one thing that published
 * it; nothing is sent to a client now but the counts.
 *
 * @param id       the anonymous log label, carrying no identity
 * @param page     the path this connection is viewing, or {@code null} between the handshake and
 *                 the first {@code join}
 * @param lastSeen when the last message arrived; the stale sweeper reads this
 * @param session  the sink to write to, already wrapped for concurrent sends by the handler
 */
public record Presence(String id, String page, Instant lastSeen, WebSocketSession session) {

	/** The same connection, now viewing {@code page}. */
	public Presence onPage(String page, Instant at) {
		return new Presence(this.id, page, at, this.session);
	}

	/** The same connection, heard from at {@code at}. */
	public Presence seenAt(Instant at) {
		return new Presence(this.id, this.page, at, this.session);
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
