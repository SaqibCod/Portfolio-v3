package dev.saq.portfolio.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusCheck;
import dev.saq.portfolio.repository.StatusCheckRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Probes one service's health endpoint and records what it found.
 *
 * <p>Three outcomes, and the thresholds that separate them are configuration rather than code:
 *
 * <ul>
 * <li><b>Operational</b> — answered 2xx inside {@code app.status.degraded-threshold}.</li>
 * <li><b>Degraded</b> — answered 2xx, but at or beyond that threshold. The service works; it is
 * just slow enough that a visitor would notice.</li>
 * <li><b>Down</b> — anything else. A non-2xx answer, a refused connection, a timeout at
 * {@code app.status.timeout}, a redirect. The distinction between "wrong answer" and "no answer"
 * is not published, so it is not drawn here either.</li>
 * </ul>
 *
 * <p>A failed probe is a successful observation, not an error: nothing thrown by the remote
 * service escapes this class, because "it is down" is exactly the information the status page
 * exists to carry. The only exceptions that get out are database failures, which the scheduler
 * logs.
 *
 * <p>Elapsed time is measured with {@link System#nanoTime()} rather than with the clock, because
 * only the former is monotonic -- an NTP correction mid-probe could otherwise produce a negative
 * round trip or a spurious degradation. The clock supplies {@code checkedAt}, where a wall-clock
 * reading is what is wanted. Both are injectable so a test can hold them still.
 */
@Service
public class HealthProbeService {

	private static final Logger log = LoggerFactory.getLogger(HealthProbeService.class);

	private final RestClient probeClient;

	private final StatusProperties statusProperties;

	private final StatusCheckRepository statusCheckRepository;

	private final Clock clock;

	private final LongSupplier nanoTime;

	public HealthProbeService(RestClient statusProbeRestClient, StatusProperties statusProperties,
			StatusCheckRepository statusCheckRepository) {

		this(statusProbeRestClient, statusProperties, statusCheckRepository, Clock.systemUTC(),
				System::nanoTime);
	}

	/** Package-private so tests can pin both the timestamp and the measured round trip. */
	HealthProbeService(RestClient probeClient, StatusProperties statusProperties,
			StatusCheckRepository statusCheckRepository, Clock clock, LongSupplier nanoTime) {

		this.probeClient = probeClient;
		this.statusProperties = statusProperties;
		this.statusCheckRepository = statusCheckRepository;
		this.clock = clock;
		this.nanoTime = nanoTime;
	}

	/**
	 * Probes one service and stores the result.
	 *
	 * @param service the service to probe
	 * @param checkedAt the timestamp to record. Passed in rather than read here so that every
	 *                  service in one scheduled sweep shares a timestamp and lands in the same
	 *                  history bucket, however long the slowest probe takes
	 * @return the stored check
	 */
	public StatusCheck probe(StatusProperties.Service service, Instant checkedAt) {
		long startedAt = nanoTime.getAsLong();
		ServiceState state;
		Integer responseTimeMs = null;

		try {
			HttpStatusCode status = probeClient.get()
					.uri(service.probeUrl())
					.retrieve()
					.toBodilessEntity()
					.getStatusCode();
			int elapsedMs = elapsedMillisSince(startedAt);

			// Checked rather than assumed: the client raises 4xx and 5xx itself, but a 3xx is
			// handed back as an ordinary response. With redirects not followed, a health
			// endpoint that answers 302 has not reported itself healthy.
			if (!status.is2xxSuccessful()) {
				state = ServiceState.DOWN;
				log.debug("Probe of {} answered {}", service.slug(), status.value());
			}
			else {
				state = elapsedMs < statusProperties.degradedThreshold().toMillis()
						? ServiceState.OPERATIONAL
						: ServiceState.DEGRADED;
				responseTimeMs = elapsedMs;
			}
		}
		catch (RestClientException ex) {
			// Covers the lot: a 4xx or 5xx body, a read timeout, a refused connection, an
			// unresolvable host. The response time stays null -- nothing was measured, and
			// recording the time spent failing would skew every average that reads the column.
			state = ServiceState.DOWN;
			log.debug("Probe of {} failed: {}", service.slug(), ex.getMessage());
		}

		if (state != ServiceState.OPERATIONAL) {
			log.info("Service {} is {}", service.slug(), state.toApi());
		}
		return statusCheckRepository.save(
				StatusCheck.record(service.slug(), checkedAt, state, responseTimeMs));
	}

	/** Probes one service, timestamped now. */
	public StatusCheck probe(StatusProperties.Service service) {
		return probe(service, clock.instant());
	}

	/** The current instant, so the scheduler can stamp a whole sweep identically. */
	public Instant now() {
		return clock.instant();
	}

	private int elapsedMillisSince(long startedAtNanos) {
		long elapsedNanos = nanoTime.getAsLong() - startedAtNanos;
		// Clamped rather than cast: a probe cannot take 25 days, but an int overflow would turn
		// a slow service into a negative round trip and a column the API publishes as a count.
		long millis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
		return (int) Math.clamp(millis, 0, Integer.MAX_VALUE);
	}

}
