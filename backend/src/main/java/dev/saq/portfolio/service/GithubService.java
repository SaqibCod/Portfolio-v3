package dev.saq.portfolio.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.saq.portfolio.client.GithubClient;
import dev.saq.portfolio.config.CacheConfig;
import dev.saq.portfolio.dto.external.github.ContributionCalendarResponse;
import dev.saq.portfolio.dto.external.github.RecentActivityResponse;
import dev.saq.portfolio.dto.external.github.RepositoryLanguagesResponse;
import dev.saq.portfolio.dto.response.GithubActivityResponse;
import dev.saq.portfolio.dto.response.GithubSummaryResponse;
import dev.saq.portfolio.exception.GithubApiException;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.mapper.GithubMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * The GitHub figures the site shows, kept at arm's length from GitHub itself.
 *
 * <p>Three mechanisms, each doing one job:
 *
 * <ul>
 * <li><b>The cache</b> shields the request path. While an entry is warm, a visitor's request
 * makes no outbound call at all, so GitHub's rate limit is spent by this application's own
 * refresh schedule and never by traffic.</li>
 * <li><b>The last good value</b> survives the cache. It is held here rather than in the cache so
 * that eviction and expiry cannot take it away: when GitHub fails, the previous answer is served
 * with its original {@code updatedAt}, which is what tells a reader how old the figures are.</li>
 * <li><b>{@link UpstreamUnavailableException}</b> is the last resort, and only for the one case
 * where there is genuinely nothing to show: GitHub failed and no fetch has ever succeeded. That
 * becomes the 503 the contract documents.</li>
 * </ul>
 *
 * <p>One consequence worth naming: a stale value returned from here is cached like any other, so
 * without help it would be served for a full TTL before GitHub was tried again. That is what
 * {@link dev.saq.portfolio.scheduler.GithubRefreshScheduler} is for — it evicts and re-primes on
 * its own interval, so recovery is bounded by the refresh period rather than by the TTL.
 */
@Service
public class GithubService {

	private static final Logger log = LoggerFactory.getLogger(GithubService.class);

	/**
	 * Single key per cache: each endpoint has exactly one answer.
	 *
	 * <p>A literal rather than the SpEL default, because the default key for a no-argument method
	 * is a shared empty key — correct here, but only by coincidence.
	 */
	public static final String SUMMARY_KEY = "'summary'";

	/**
	 * The contract's {@code limit} ceiling, and the number of items always fetched.
	 *
	 * <p>Fetching the maximum regardless of what was asked for means the last good value can
	 * serve any permitted {@code limit}, rather than only the one that happened to be requested
	 * when GitHub was last reachable.
	 */
	public static final int MAX_ACTIVITY_ITEMS = 20;

	/**
	 * The contract's default {@code limit}, which is also the only size the site asks for.
	 *
	 * <p>Named here because the activity cache is keyed by {@code limit}, so each size is its own
	 * entry and the refresh job has to know which one a visitor will actually hit — priming only
	 * {@link #MAX_ACTIVITY_ITEMS} would leave the default cold. {@code GithubController} repeats
	 * the number as a string, because {@code @RequestParam} needs one; a test pins the two
	 * together.
	 */
	public static final int DEFAULT_ACTIVITY_ITEMS = 5;

	private final GithubClient githubClient;

	private final GithubMapper githubMapper;

	/** The newest summary GitHub has ever returned, or null if it never has. */
	private final AtomicReference<GithubSummaryResponse> lastGoodSummary = new AtomicReference<>();

	/** As above, holding {@link #MAX_ACTIVITY_ITEMS} items so any limit can be served from it. */
	private final AtomicReference<GithubActivityResponse> lastGoodActivity = new AtomicReference<>();

	public GithubService(GithubClient githubClient, GithubMapper githubMapper) {
		this.githubClient = githubClient;
		this.githubMapper = githubMapper;
	}

	/**
	 * The contribution graph, top languages and totals.
	 *
	 * @throws UpstreamUnavailableException if GitHub is unreachable and nothing was ever cached
	 */
	@Cacheable(cacheNames = CacheConfig.GITHUB_SUMMARY_CACHE, key = SUMMARY_KEY)
	public GithubSummaryResponse getSummary() {
		try {
			ContributionCalendarResponse.ContributionCalendar calendar =
					githubClient.fetchContributionCalendar();
			List<RepositoryLanguagesResponse.Repository> repositories =
					githubClient.fetchLanguageBytes();

			GithubSummaryResponse summary = githubMapper.toSummary(githubClient.username(),
					calendar, repositories, Instant.now());
			lastGoodSummary.set(summary);
			return summary;
		}
		catch (GithubApiException | RestClientException ex) {
			return lastGoodOrUnavailable(lastGoodSummary.get(), "summary", ex);
		}
	}

	/**
	 * Recent commits, pull requests and releases, newest first.
	 *
	 * @param limit how many items to return, 1–{@value #MAX_ACTIVITY_ITEMS}
	 * @throws UpstreamUnavailableException if GitHub is unreachable and nothing was ever cached
	 */
	@Cacheable(cacheNames = CacheConfig.GITHUB_ACTIVITY_CACHE, key = "#limit")
	public GithubActivityResponse getActivity(int limit) {
		try {
			RecentActivityResponse.User user = githubClient.fetchRecentActivity();
			GithubActivityResponse full = githubMapper.toActivity(user, MAX_ACTIVITY_ITEMS,
					Instant.now());
			lastGoodActivity.set(full);
			return trim(full, limit);
		}
		catch (GithubApiException | RestClientException ex) {
			GithubActivityResponse lastGood = lastGoodActivity.get();
			return trim(lastGoodOrUnavailable(lastGood, "activity", ex), limit);
		}
	}

	/**
	 * Drops the summary cache entry, so the next call re-reads GitHub.
	 *
	 * <p>For {@link dev.saq.portfolio.scheduler.GithubRefreshScheduler}. Safe even when GitHub is
	 * down: the last good value is not in the cache, so evicting cannot discard it.
	 */
	@CacheEvict(cacheNames = CacheConfig.GITHUB_SUMMARY_CACHE, allEntries = true)
	public void evictSummary() {
		// The annotation is the whole method; see the class javadoc for why eviction is safe.
	}

	/** Drops every activity entry, one per {@code limit} that has been asked for. */
	@CacheEvict(cacheNames = CacheConfig.GITHUB_ACTIVITY_CACHE, allEntries = true)
	public void evictActivity() {
	}

	/**
	 * Serves the previous answer, or gives up.
	 *
	 * <p>Logged at warn rather than error: one failed GitHub call while a good value is held is
	 * not a fault in this application, and a reader cannot tell the difference. Only the case
	 * with nothing to serve becomes a 503.
	 */
	private <T> T lastGoodOrUnavailable(T lastGood, String what, RuntimeException cause) {
		if (lastGood != null) {
			log.warn("GitHub {} fetch failed; serving the last good value: {}", what,
					cause.getMessage());
			return lastGood;
		}
		log.warn("GitHub {} fetch failed and nothing has ever been cached", what, cause);
		throw new UpstreamUnavailableException(
				"GitHub data is temporarily unavailable. Try again shortly.", cause);
	}

	/** The first {@code limit} items of a full-size response, keeping its original timestamp. */
	private static GithubActivityResponse trim(GithubActivityResponse full, int limit) {
		List<GithubActivityResponse.Item> items = full.items();
		if (limit >= items.size()) {
			return full;
		}
		return new GithubActivityResponse(List.copyOf(items.subList(0, Math.max(limit, 0))),
				full.updatedAt());
	}

}
