package dev.saq.portfolio.scheduler;

import java.util.concurrent.TimeUnit;

import dev.saq.portfolio.service.GithubService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-reads GitHub shortly before each cache entry expires, so a visitor's request never has to.
 *
 * <p>Evict, then read. Eviction alone would leave the next visitor paying for the fetch, and a
 * read alone would be answered from the still-warm cache and fetch nothing. Both calls go through
 * {@link GithubService}'s proxy because this is a separate bean, which is what makes the cache
 * annotations on those methods apply at all.
 *
 * <p>This is also the recovery path. A failed fetch leaves {@code GithubService} serving its last
 * good value, which then sits in the cache like any other entry — so without a job running on a
 * shorter cycle than the TTL, a single GitHub outage would freeze the figures for six hours.
 *
 * <p>Inactive under the {@code test} profile: the integration tests assert on exactly how many
 * times GitHub is called, and a job firing mid-test would be a second, invisible caller.
 */
@Component
@Profile("!test")
public class GithubRefreshScheduler {

	private static final Logger log = LoggerFactory.getLogger(GithubRefreshScheduler.class);

	/** Comfortably inside the summary cache's six hours. */
	private static final long SUMMARY_REFRESH_MINUTES = 330;

	/** Comfortably inside the activity cache's fifteen minutes. */
	private static final long ACTIVITY_REFRESH_MINUTES = 14;

	/**
	 * Let the application finish starting first. Both jobs still prime the caches within a minute
	 * of boot, so the first visitor after a deploy is served from a warm cache.
	 */
	private static final long INITIAL_DELAY_SECONDS = 20;

	private final GithubService githubService;

	public GithubRefreshScheduler(GithubService githubService) {
		this.githubService = githubService;
	}

	/**
	 * {@code fixedDelay}, not {@code fixedRate}: the next refresh is measured from the end of the
	 * last one, so a slow GitHub can never queue overlapping fetches.
	 */
	@Scheduled(initialDelay = INITIAL_DELAY_SECONDS, fixedDelay = SUMMARY_REFRESH_MINUTES * 60,
			timeUnit = TimeUnit.SECONDS)
	public void refreshSummary() {
		refresh("summary", githubService::evictSummary, githubService::getSummary);
	}

	/**
	 * Primes the default size, which is the entry a visitor's request lands on.
	 *
	 * <p>The activity cache is keyed by {@code limit}, so only the size primed here is warm — but
	 * one read is still enough for both jobs this has to do. Every fetch maps the full twenty
	 * items into the last good value regardless of the size asked for, so priming the default
	 * also leaves enough held to serve any other {@code limit} while GitHub is down.
	 */
	@Scheduled(initialDelay = INITIAL_DELAY_SECONDS, fixedDelay = ACTIVITY_REFRESH_MINUTES * 60,
			timeUnit = TimeUnit.SECONDS)
	public void refreshActivity() {
		refresh("activity", githubService::evictActivity,
				() -> githubService.getActivity(GithubService.DEFAULT_ACTIVITY_ITEMS));
	}

	/**
	 * Nothing thrown from a refresh is allowed to escape.
	 *
	 * <p>An exception out of a {@code @Scheduled} method is logged by the framework and the job is
	 * retried on the next tick, so the behaviour would be the same — but a failed refresh here is
	 * an expected event, not an error, and the service has already logged it with its own
	 * context. Swallowing it keeps one GitHub outage from filling the log with stack traces.
	 */
	private void refresh(String what, Runnable evict, Runnable prime) {
		try {
			evict.run();
			prime.run();
			log.debug("Refreshed the GitHub {} cache", what);
		}
		catch (RuntimeException ex) {
			log.warn("Scheduled GitHub {} refresh did not complete: {}", what, ex.getMessage());
		}
	}

}
