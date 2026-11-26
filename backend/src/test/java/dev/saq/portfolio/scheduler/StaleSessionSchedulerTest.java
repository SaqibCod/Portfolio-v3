package dev.saq.portfolio.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.saq.portfolio.service.PresenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The job is a trigger and an error boundary; which sessions are stale is
 * {@code PresenceServiceTest}'s subject, since the registry is the only thing that knows.
 */
class StaleSessionSchedulerTest {

	private PresenceService presenceService;

	private StaleSessionScheduler scheduler;

	@BeforeEach
	void setUp() {
		this.presenceService = mock(PresenceService.class);
		this.scheduler = new StaleSessionScheduler(this.presenceService);
	}

	@Test
	void sweepsTheRegistry() {
		when(presenceService.closeStaleSessions()).thenReturn(3);

		scheduler.closeStaleSessions();

		verify(presenceService).closeStaleSessions();
	}

	@Test
	void runsWhenThereIsNothingToSweep() {
		// The common case on a quiet site: every connection is beating, so the sweep closes none.
		when(presenceService.closeStaleSessions()).thenReturn(0);

		assertThatCode(() -> scheduler.closeStaleSessions()).doesNotThrowAnyException();
	}

	@Test
	void aFailedSweepIsNotAnOutage() {
		// This runs every fifteen seconds. A missed pass costs one more round of slightly
		// inflated counts, which the next run corrects; a stack trace would cost more.
		when(presenceService.closeStaleSessions())
				.thenThrow(new IllegalStateException("registry unavailable"));

		assertThatCode(() -> scheduler.closeStaleSessions()).doesNotThrowAnyException();
	}

}
