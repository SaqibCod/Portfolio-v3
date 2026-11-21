package dev.saq.portfolio.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The server-side caches, which are what keep GitHub's rate limits and outages away from
 * visitors: a warm cache serves every request without a single outbound call.
 *
 * <p>Declared as an explicit {@link CaffeineCacheManager} rather than through
 * {@code spring.cache.caffeine.spec}, because that property applies one specification to every
 * cache and these two need different lifetimes. {@code registerCustomCache} is the supported way
 * to give each cache its own {@link Caffeine} builder.
 *
 * <p>Each cache holds a handful of entries at most — one per endpoint, keyed by nothing or by the
 * requested page size — so the bound exists only to stop an unforeseen key explosion from growing
 * without limit.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

	/** {@code GET /api/v1/github/summary}. Contribution calendar and language totals. */
	public static final String GITHUB_SUMMARY_CACHE = "githubSummary";

	/** {@code GET /api/v1/github/activity}. Recent commits, pull requests and releases. */
	public static final String GITHUB_ACTIVITY_CACHE = "githubActivity";

	/**
	 * How long a summary stays fresh. A contribution graph and a language breakdown move slowly,
	 * and the contract documents six hours.
	 */
	public static final Duration SUMMARY_TTL = Duration.ofHours(6);

	/** How long an activity list stays fresh. The contract documents fifteen minutes. */
	public static final Duration ACTIVITY_TTL = Duration.ofMinutes(15);

	/** Generous for one entry per endpoint; a ceiling against an unexpected key, not a budget. */
	private static final long MAX_ENTRIES = 64;

	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.registerCustomCache(GITHUB_SUMMARY_CACHE, expiringAfter(SUMMARY_TTL));
		cacheManager.registerCustomCache(GITHUB_ACTIVITY_CACHE, expiringAfter(ACTIVITY_TTL));
		// Nothing cached here is ever absent: a method that cannot produce a value throws rather
		// than returning null. Refusing nulls turns a future regression into a loud failure
		// instead of a cache entry that quietly means "unknown".
		cacheManager.setAllowNullValues(false);
		return cacheManager;
	}

	/**
	 * {@code expireAfterWrite}, not {@code expireAfterAccess}: these entries must age out on a
	 * fixed schedule regardless of traffic. Under access-based expiry a popular endpoint would
	 * keep refreshing its own entry and could serve the same figures indefinitely.
	 */
	private static com.github.benmanes.caffeine.cache.Cache<Object, Object> expiringAfter(
			Duration ttl) {

		return Caffeine.newBuilder()
				.expireAfterWrite(ttl)
				.maximumSize(MAX_ENTRIES)
				.build();
	}

}
