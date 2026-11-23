package dev.saq.portfolio.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.service.HealthProbeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Probes every monitored service on {@code app.status.interval}.
 *
 * <p>All services are probed concurrently on virtual threads, so one unreachable project waits
 * out its own timeout without delaying the rest: a sweep costs about as long as its slowest
 * probe rather than the sum of them. Virtual threads make that free — each probe spends its life
 * blocked on a socket, which is the case they exist for, so there is no pool to size.
 *
 * <p>Every probe in a sweep is stamped with one timestamp, taken before the first request goes
 * out. Reading the clock per probe would scatter a sweep across two history buckets whenever it
 * straddled a boundary, and a bucket is meant to answer "what was true during these five
 * minutes" rather than "which probes happened to finish inside them".
 *
 * <p>With nothing configured there is nothing to do, which is the state the application ships in
 * until a project is deployed. The job still fires, finds an empty list, and returns — there is
 * no "monitoring is off" switch to forget to flip.
 *
 * <p>Inactive under the {@code test} profile, like the GitHub refresh job: an integration test
 * must not have an invisible second caller making outbound HTTP requests and writing rows.
 */
@Component
@Profile("!test")
public class HealthProbeScheduler {

	private static final Logger log = LoggerFactory.getLogger(HealthProbeScheduler.class);

	/**
	 * Let the application finish starting before the first sweep, so startup is not competing
	 * with a round of outbound requests. Short enough that the page has data within a minute of
	 * a deploy.
	 */
	private static final String INITIAL_DELAY = "15s";

	private final StatusProperties statusProperties;

	private final HealthProbeService healthProbeService;

	public HealthProbeScheduler(StatusProperties statusProperties,
			HealthProbeService healthProbeService) {

		this.statusProperties = statusProperties;
		this.healthProbeService = healthProbeService;
	}

	/**
	 * {@code fixedRate}, not {@code fixedDelay}: a probe interval is a sampling rate, and the
	 * history buckets assume an even one. Overlap is not a concern because
	 * {@code app.status.timeout} is necessarily shorter than the interval.
	 */
	@Scheduled(fixedRateString = "${app.status.interval}", initialDelayString = INITIAL_DELAY)
	public void probeAll() {
		List<StatusProperties.Service> services = statusProperties.services();
		if (services.isEmpty()) {
			log.debug("No services configured, nothing to probe");
			return;
		}

		Instant checkedAt = healthProbeService.now();
		List<Callable<Void>> probes = services.stream()
				.map(service -> (Callable<Void>) () -> {
					probeQuietly(service, checkedAt);
					return null;
				})
				.toList();

		// invokeAll waits for every probe, so the executor closes with nothing in flight and one
		// sweep can never overlap the next. The futures are not inspected: probeQuietly() has
		// already handled every outcome, and each probe's result is a row rather than a value.
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			executor.invokeAll(probes);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Probe sweep interrupted");
		}
	}

	/**
	 * One probe, with nothing allowed to escape.
	 *
	 * <p>{@link HealthProbeService} already treats an unreachable service as an observation
	 * rather than an error, so anything thrown here is the database refusing the insert. That is
	 * worth a line in the log and nothing more: the other probes in the sweep are unaffected,
	 * and the next sweep will try again.
	 */
	private void probeQuietly(StatusProperties.Service service, Instant checkedAt) {
		try {
			healthProbeService.probe(service, checkedAt);
		}
		catch (RuntimeException ex) {
			log.warn("Could not record a probe of {}: {}", service.slug(), ex.getMessage());
		}
	}

}
