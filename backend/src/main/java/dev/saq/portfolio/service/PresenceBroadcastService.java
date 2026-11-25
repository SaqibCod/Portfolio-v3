package dev.saq.portfolio.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Rate gate in front of the presence counts: at most one broadcast per page per second.
 *
 * <p>Counts change on every join, every route change and every disconnect, and each change would
 * otherwise write a message to every viewer of the affected page. A single visitor clicking
 * through five links is five broadcasts to everyone, and a page with twenty viewers turns that
 * into a hundred frames to say almost nothing.
 *
 * <p>Coalescing here is leading-edge with a trailing send, not "drop extras". The first change in
 * a quiet second goes out immediately, so a visitor who joins sees the count at once; further
 * changes inside that second schedule one send at the end of the window instead. Dropping them
 * would be cheaper and wrong -- the last change in a busy second is the one that holds the
 * current count, and discarding it leaves every client reading a stale number until the next
 * unrelated change happens to come along.
 *
 * <p>What to send is not this class's business. Callers pass the work as a {@link Runnable},
 * which is re-run at trailing time so a delayed broadcast carries the counts as they are when it
 * fires rather than as they were when it was requested. That is also what keeps this class free
 * of a dependency on {@link PresenceService}, which depends on it.
 */
@Service
public class PresenceBroadcastService {

	private static final Logger log = LoggerFactory.getLogger(PresenceBroadcastService.class);

	/** One second, as the LLD specifies. Package-private so a test can reason in the same unit. */
	static final Duration WINDOW = Duration.ofSeconds(1);

	/**
	 * Pages are evicted a few windows after their last broadcast, which bounds this map by the
	 * number of pages seen recently rather than by the number of distinct paths a client can
	 * invent. An entry past its window is indistinguishable from a missing one -- both mean
	 * "send now" -- so eviction can never change a decision.
	 */
	private static final long MAX_TRACKED_PAGES = 10_000;

	private final TaskScheduler taskScheduler;

	private final Clock clock;

	private final Cache<String, Window> windows;

	/** What one page's gate knows: when it last sent, and whether a trailing send is already due. */
	private record Window(long lastSentMillis, boolean trailingPending) {
	}

	private enum Decision {
		SEND_NOW, SCHEDULE_TRAILING, ALREADY_PENDING
	}

	@Autowired
	public PresenceBroadcastService(TaskScheduler taskScheduler) {
		this(taskScheduler, Clock.systemUTC());
	}

	/** Package-private so a test can drive the window without waiting out real seconds. */
	PresenceBroadcastService(TaskScheduler taskScheduler, Clock clock) {
		this.taskScheduler = taskScheduler;
		this.clock = clock;
		this.windows = Caffeine.newBuilder()
				.maximumSize(MAX_TRACKED_PAGES)
				.expireAfterWrite(WINDOW.multipliedBy(5))
				.build();
	}

	/**
	 * Runs {@code broadcast} now, or schedules it for the end of this page's current window, or
	 * does nothing because a send is already due.
	 *
	 * @param page      the page whose counts changed
	 * @param broadcast the send to perform; may be run on a scheduler thread, and may be run
	 *                  later than this call, so it must read the counts itself
	 */
	public void requestBroadcast(String page, Runnable broadcast) {
		long now = this.clock.millis();
		AtomicReference<Decision> decision = new AtomicReference<>();
		// compute() holds the entry's lock for the whole decision, so two threads reporting a
		// change to the same page at the same moment cannot both decide to schedule.
		Window window = this.windows.asMap().compute(page, (unused, current) -> {
			if (current == null || now - current.lastSentMillis() >= WINDOW.toMillis()) {
				decision.set(Decision.SEND_NOW);
				return new Window(now, false);
			}
			if (current.trailingPending()) {
				decision.set(Decision.ALREADY_PENDING);
				return current;
			}
			decision.set(Decision.SCHEDULE_TRAILING);
			return new Window(current.lastSentMillis(), true);
		});

		switch (decision.get()) {
			case SEND_NOW -> run(page, broadcast);
			case SCHEDULE_TRAILING -> this.taskScheduler.schedule(
					() -> sendTrailing(page, broadcast),
					Instant.ofEpochMilli(window.lastSentMillis() + WINDOW.toMillis()));
			case ALREADY_PENDING -> {
				// The pending send will carry this change too, because it reads the counts fresh.
			}
		}
	}

	private void sendTrailing(String page, Runnable broadcast) {
		this.windows.put(page, new Window(this.clock.millis(), false));
		run(page, broadcast);
	}

	/**
	 * A broadcast that throws must not escape: on the trailing path this runs on a scheduler
	 * thread, where an exception would be reported as a failed task and tell nobody anything
	 * useful. A missed presence update is invisible to visitors and corrected by the next one.
	 */
	private void run(String page, Runnable broadcast) {
		try {
			broadcast.run();
		}
		catch (RuntimeException ex) {
			log.debug("Presence broadcast for {} did not complete: {}", page, ex.getMessage());
		}
	}

}
