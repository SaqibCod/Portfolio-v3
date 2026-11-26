package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.saq.testsupport.MutableClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

/**
 * The coalescing rule, which is the whole class: one broadcast per page per second, with the
 * last change in a busy second still arriving rather than being dropped.
 */
class PresenceBroadcastServiceTest {

	private static final Instant START = Instant.parse("2026-11-25T10:00:00Z");

	private static final String PAGE = "/projects/githmind";

	private MutableClock clock;

	private TaskScheduler taskScheduler;

	private PresenceBroadcastService service;

	private AtomicInteger broadcasts;

	private Runnable countingBroadcast;

	@BeforeEach
	void setUp() {
		this.clock = new MutableClock(START);
		this.taskScheduler = mock(TaskScheduler.class);
		this.service = new PresenceBroadcastService(this.taskScheduler, this.clock);
		this.broadcasts = new AtomicInteger();
		this.countingBroadcast = this.broadcasts::incrementAndGet;
	}

	@Test
	void sendsTheFirstChangeImmediately() {
		// A visitor who has just joined must not wait out a window to see the count.
		service.requestBroadcast(PAGE, countingBroadcast);

		assertThat(broadcasts).hasValue(1);
		verifyNoInteractions(taskScheduler);
	}

	@Test
	void doesNotSendASecondChangeInsideTheSameSecond() {
		service.requestBroadcast(PAGE, countingBroadcast);
		clock.advance(Duration.ofMillis(300));

		service.requestBroadcast(PAGE, countingBroadcast);

		assertThat(broadcasts).hasValue(1);
	}

	@Test
	void schedulesTheSuppressedChangeForTheEndOfTheWindow() {
		service.requestBroadcast(PAGE, countingBroadcast);
		clock.advance(Duration.ofMillis(300));
		service.requestBroadcast(PAGE, countingBroadcast);

		// Due one second after the send that opened the window, not one second from now.
		verify(taskScheduler).schedule(any(Runnable.class), eq(START.plusSeconds(1)));
	}

	@Test
	void theTrailingSendCarriesTheCountsAsTheyAreWhenItFires() {
		List<String> sends = new ArrayList<>();
		// Re-reads the clock each time it runs, the way broadcastPresence re-reads the registry.
		Runnable recordingBroadcast = () -> sends.add(clock.instant().toString());

		service.requestBroadcast(PAGE, recordingBroadcast);
		clock.advance(Duration.ofMillis(300));
		service.requestBroadcast(PAGE, recordingBroadcast);
		clock.advance(Duration.ofMillis(700));
		runScheduledTask();

		assertThat(sends).containsExactly(
				START.toString(),
				START.plusSeconds(1).toString());
	}

	@Test
	void collapsesManyChangesInOneSecondIntoASingleTrailingSend() {
		service.requestBroadcast(PAGE, countingBroadcast);
		for (int i = 0; i < 20; i++) {
			clock.advance(Duration.ofMillis(10));
			service.requestBroadcast(PAGE, countingBroadcast);
		}

		// One scheduled task for all twenty, not twenty.
		verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
		clock.advance(Duration.ofSeconds(1));
		runScheduledTask();
		assertThat(broadcasts).hasValue(2);
	}

	@Test
	void opensAFreshWindowOnceTheLastOneHasPassed() {
		service.requestBroadcast(PAGE, countingBroadcast);
		clock.advance(Duration.ofSeconds(1));

		service.requestBroadcast(PAGE, countingBroadcast);

		assertThat(broadcasts).hasValue(2);
		verifyNoInteractions(taskScheduler);
	}

	@Test
	void aTrailingSendOpensTheNextWindowFromWhenItFired() {
		service.requestBroadcast(PAGE, countingBroadcast);
		clock.advance(Duration.ofMillis(300));
		service.requestBroadcast(PAGE, countingBroadcast);
		clock.advance(Duration.ofMillis(700));
		runScheduledTask();

		// Immediately after the trailing send, the window is closed again.
		service.requestBroadcast(PAGE, countingBroadcast);
		assertThat(broadcasts).hasValue(2);
	}

	@Test
	void gatesEachPageSeparately() {
		service.requestBroadcast(PAGE, countingBroadcast);
		clock.advance(Duration.ofMillis(100));

		// A busy window on one page must not silence a first change on another.
		service.requestBroadcast("/guestbook", countingBroadcast);

		assertThat(broadcasts).hasValue(2);
		verifyNoInteractions(taskScheduler);
	}

	@Test
	void aBroadcastThatThrowsDoesNotEscape() {
		// On the trailing path this runs on a scheduler thread, where an exception would be
		// reported as a failed task and tell nobody anything useful.
		service.requestBroadcast(PAGE, () -> {
			throw new IllegalStateException("send failed");
		});

		// Reaching here is the assertion; the window must also have opened as normal.
		clock.advance(Duration.ofMillis(100));
		service.requestBroadcast(PAGE, countingBroadcast);
		assertThat(broadcasts).hasValue(0);
	}

	/** Runs whatever was handed to the scheduler, as the scheduler thread would. */
	private void runScheduledTask() {
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).schedule(task.capture(), any(Instant.class));
		task.getValue().run();
	}

}
