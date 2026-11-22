package dev.saq.portfolio.scheduler;

import java.time.Clock;
import java.time.Instant;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.repository.StatusCheckRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes probe results past {@code app.status.retention}, once a night.
 *
 * <p>At a five-minute interval each service produces about 8,600 rows a month, so without this
 * the table would grow without limit to hold history no endpoint can reach -- the longest window
 * the contract offers is thirty days. The retention default is thirty-five, which leaves the
 * oldest served bucket comfortably clear of the sweep.
 *
 * <p>Runs regardless of whether anything is configured. Services come and go, and a project
 * removed from the list leaves rows behind that nothing else would ever clean up.
 *
 * <p>Nightly and off-peak rather than hourly: the delete is a range scan over an indexed
 * timestamp, and doing it once covers a day's worth of expiry just as well as twenty-four
 * partial passes.
 *
 * <p>Inactive under the {@code test} profile, so a sweep can never fire in the middle of a test
 * that just seeded history.
 */
@Component
@Profile("!test")
public class StatusRetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(StatusRetentionScheduler.class);

	/** 03:20 UTC, a quiet hour, and off the hour so it does not pile onto other nightly work. */
	private static final String DAILY_AT_0320_UTC = "0 20 3 * * *";

	private final StatusProperties statusProperties;

	private final StatusCheckRepository statusCheckRepository;

	private final Clock clock;

	public StatusRetentionScheduler(StatusProperties statusProperties,
			StatusCheckRepository statusCheckRepository) {

		this(statusProperties, statusCheckRepository, Clock.systemUTC());
	}

	/** Package-private so a test can pin the cutoff. */
	StatusRetentionScheduler(StatusProperties statusProperties,
			StatusCheckRepository statusCheckRepository, Clock clock) {

		this.statusProperties = statusProperties;
		this.statusCheckRepository = statusCheckRepository;
		this.clock = clock;
	}

	@Scheduled(cron = DAILY_AT_0320_UTC, zone = "UTC")
	public void deleteExpiredChecks() {
		Instant cutoff = clock.instant().minus(statusProperties.retention());
		try {
			int deleted = statusCheckRepository.deleteOlderThan(cutoff);
			if (deleted > 0) {
				log.info("Deleted {} probe results older than {}", deleted, cutoff);
			}
		}
		catch (RuntimeException ex) {
			// A failed sweep is not an outage: the only cost is a day of rows that tonight's
			// run will collect instead. A stack trace on a nightly job is noise.
			log.warn("Status retention sweep did not complete: {}", ex.getMessage());
		}
	}

}
