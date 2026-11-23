package dev.saq.portfolio.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.repository.StatusCheckRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The cutoff the nightly sweep computes, and that a failed sweep is not an outage.
 * {@code StatusCheckRepositoryTest} covers the delete itself against a real database.
 */
class StatusRetentionSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-11-22T03:20:00Z");

	private StatusCheckRepository repository;

	@BeforeEach
	void setUp() {
		this.repository = mock(StatusCheckRepository.class);
		when(repository.deleteOlderThan(any())).thenReturn(0);
	}

	@Test
	void deletesEverythingOlderThanTheRetentionWindow() {
		scheduler(Duration.ofDays(35)).deleteExpiredChecks();

		verify(repository).deleteOlderThan(Instant.parse("2026-10-18T03:20:00Z"));
	}

	@Test
	void followsTheConfiguredRetentionRatherThanAFixedPeriod() {
		scheduler(Duration.ofDays(7)).deleteExpiredChecks();

		verify(repository).deleteOlderThan(Instant.parse("2026-11-15T03:20:00Z"));
	}

	@Test
	void runsEvenWhenNothingIsMonitored() {
		// A project removed from the list leaves rows behind that nothing else would collect.
		scheduler(Duration.ofDays(35)).deleteExpiredChecks();

		verify(repository).deleteOlderThan(any());
	}

	@Test
	void swallowsADatabaseFailureRatherThanLettingTheJobThrow() {
		when(repository.deleteOlderThan(any()))
				.thenThrow(new DataAccessResourceFailureException("connection lost"));

		StatusRetentionScheduler scheduler = scheduler(Duration.ofDays(35));

		assertThatCode(scheduler::deleteExpiredChecks).doesNotThrowAnyException();
	}

	private StatusRetentionScheduler scheduler(Duration retention) {
		StatusProperties properties = new StatusProperties(Duration.ofMinutes(5),
				Duration.ofSeconds(10), Duration.ofSeconds(2), retention, List.of());
		return new StatusRetentionScheduler(properties, repository,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

}
