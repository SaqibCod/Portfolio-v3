package dev.saq.portfolio.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.service.HealthProbeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * What one sweep does. The probe itself is mocked -- {@code HealthProbeServiceTest} owns the
 * classification -- so what is left is the scheduler's own three jobs: visit everything, stamp it
 * all with one timestamp, and survive both an empty service list and a failing insert.
 */
class HealthProbeSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-11-22T10:37:41Z");

	private static final StatusProperties.Service GITHMIND = new StatusProperties.Service(
			"githmind", "githMind", "https://githmind.example.com", "/actuator/health");

	private static final StatusProperties.Service MEDI_SCAN = new StatusProperties.Service(
			"medi-scan", "Medi-Scan", "https://medi-scan.example.com", "/actuator/health");

	private HealthProbeService probeService;

	@BeforeEach
	void setUp() {
		this.probeService = mock(HealthProbeService.class);
		when(probeService.now()).thenReturn(NOW);
	}

	/**
	 * The configuration the application ships in. The job still fires on its interval; it simply
	 * has nowhere to go, and must not treat that as a failure.
	 */
	@Test
	void probesNothingAndRaisesNothingWhenNoServicesAreConfigured() {
		HealthProbeScheduler scheduler = scheduler(List.of());

		assertThatCode(scheduler::probeAll).doesNotThrowAnyException();
		verifyNoInteractions(probeService);
	}

	@Test
	void probesEveryConfiguredService() {
		scheduler(List.of(GITHMIND, MEDI_SCAN)).probeAll();

		verify(probeService).probe(eq(GITHMIND), any());
		verify(probeService).probe(eq(MEDI_SCAN), any());
	}

	@Test
	void stampsTheWholeSweepWithOneTimestamp() {
		// Reading the clock per probe would scatter a sweep across two history buckets whenever
		// it straddled a boundary.
		scheduler(List.of(GITHMIND, MEDI_SCAN)).probeAll();

		verify(probeService).probe(GITHMIND, NOW);
		verify(probeService).probe(MEDI_SCAN, NOW);
		verify(probeService, times(1)).now();
	}

	@Test
	void keepsProbingTheRestWhenOneProbeCannotBeRecorded() {
		// Only a database failure reaches here: HealthProbeService turns an unreachable service
		// into a stored DOWN check rather than into an exception.
		doThrow(new DataAccessResourceFailureException("connection lost"))
				.when(probeService).probe(eq(GITHMIND), any());

		HealthProbeScheduler scheduler = scheduler(List.of(GITHMIND, MEDI_SCAN));

		assertThatCode(scheduler::probeAll).doesNotThrowAnyException();
		verify(probeService).probe(MEDI_SCAN, NOW);
	}

	@Test
	void probesServicesInOneSweepRatherThanOnePerCall() {
		scheduler(List.of(GITHMIND, MEDI_SCAN, frontend())).probeAll();

		verify(probeService, times(3)).probe(any(), eq(NOW));
	}

	@Test
	void sweepsAgainOnTheNextCall() {
		HealthProbeScheduler scheduler = scheduler(List.of(GITHMIND));

		scheduler.probeAll();
		scheduler.probeAll();

		verify(probeService, times(2)).probe(GITHMIND, NOW);
	}

	@Test
	void theConfiguredIntervalIsWhatTheJobWouldFireOn() {
		// The annotation resolves "${app.status.interval}", so the value that matters is the one
		// StatusProperties holds -- pinned here so a change to the default is deliberate.
		assertThat(properties(List.of()).interval()).isEqualTo(Duration.ofMinutes(5));
	}

	private HealthProbeScheduler scheduler(List<StatusProperties.Service> services) {
		return new HealthProbeScheduler(properties(services), probeService);
	}

	private static StatusProperties properties(List<StatusProperties.Service> services) {
		return new StatusProperties(Duration.ofMinutes(5), Duration.ofSeconds(10),
				Duration.ofSeconds(2), Duration.ofDays(35), services);
	}

	private static StatusProperties.Service frontend() {
		return new StatusProperties.Service("frontend", "Frontend", "https://example.com", "/");
	}

}
