package dev.saq.portfolio.scheduler;

import dev.saq.portfolio.service.PresenceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes presence connections that have gone quiet, every fifteen seconds.
 *
 * <p>A WebSocket does not reliably announce its own death. A laptop lid closing, a phone losing
 * signal or a proxy timing out all leave a socket the container still believes is open, and every
 * one of those would otherwise be counted as a person viewing the page forever -- so the count
 * drifts upwards and never comes back down.
 *
 * <p>Fifteen seconds against a sixty-second staleness window means a dead connection is noticed
 * within about a minute and a quarter at worst, and a healthy one that beats every twenty-five
 * seconds is never close to the cutoff.
 *
 * <p>Inactive under the {@code test} profile, so a sweep can never close a socket in the middle
 * of a test that just opened it. {@code StaleSessionSchedulerTest} calls the method directly
 * instead.
 */
@Component
@Profile("!test")
public class StaleSessionScheduler {

	private static final Logger log = LoggerFactory.getLogger(StaleSessionScheduler.class);

	private static final long EVERY_15_SECONDS_MILLIS = 15_000L;

	private final PresenceService presenceService;

	public StaleSessionScheduler(PresenceService presenceService) {
		this.presenceService = presenceService;
	}

	@Scheduled(fixedDelay = EVERY_15_SECONDS_MILLIS)
	public void closeStaleSessions() {
		try {
			int closed = this.presenceService.closeStaleSessions();
			if (closed > 0) {
				log.debug("Closed {} stale presence sessions", closed);
			}
		}
		catch (RuntimeException ex) {
			// A missed sweep costs one more pass of slightly inflated counts, which the next run
			// corrects. A stack trace every fifteen seconds would cost more than that.
			log.warn("Stale presence sweep did not complete: {}", ex.getMessage());
		}
	}

}
