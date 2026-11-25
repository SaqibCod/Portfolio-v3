package dev.saq.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock a test can wind forward.
 *
 * <p>Presence is full of one-second windows -- the broadcast gate, the cursor budget -- and the
 * only two ways to test a window are to sleep through it or to move the clock. Sleeping would
 * add real seconds to every run and still be a race on a slow machine, so these tests move the
 * clock instead and stay deterministic.
 */
public final class MutableClock extends Clock {

	private final ZoneId zone;

	private Instant instant;

	public MutableClock(Instant instant) {
		this(instant, ZoneId.of("UTC"));
	}

	private MutableClock(Instant instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	@Override
	public ZoneId getZone() {
		return this.zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(this.instant, zone);
	}

	@Override
	public Instant instant() {
		return this.instant;
	}

	/** Moves time forward. Returns this, so a test can advance and act on one line. */
	public MutableClock advance(Duration amount) {
		this.instant = this.instant.plus(amount);
		return this;
	}

}
