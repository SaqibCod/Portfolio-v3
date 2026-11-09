package dev.saq.portfolio.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.saq.portfolio.dto.internal.RateLimitResult;
import dev.saq.portfolio.model.RateLimitPolicy;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.stereotype.Service;

/**
 * In-memory rate limiting: one Bucket4j token bucket per policy and caller, held in a Caffeine
 * cache so idle callers are evicted instead of leaking memory.
 *
 * <p>Callers are identified by hashed IP only (see
 * {@link dev.saq.portfolio.util.ClientIpUtils}), so no raw address is ever held here.
 *
 * <p>State is per instance. That is deliberate for a single-node deployment; a clustered
 * deployment would swap the cache for one of Bucket4j's distributed back ends.
 */
@Service
public class RateLimiterService {

	/** Idle buckets are dropped a little after their window, when they are full again anyway. */
	private static final Duration EVICTION_MARGIN = Duration.ofMinutes(5);

	private static final long MAX_TRACKED_CALLERS = 100_000;

	private final Cache<String, Bucket> buckets;

	public RateLimiterService() {
		this(Caffeine.newBuilder()
				.maximumSize(MAX_TRACKED_CALLERS)
				.expireAfterAccess(longestWindow().plus(EVICTION_MARGIN))
				.build());
	}

	RateLimiterService(Cache<String, Bucket> buckets) {
		this.buckets = buckets;
	}

	/**
	 * Takes one token for {@code ipHash} under {@code policy}.
	 *
	 * @return the outcome, including the header values the caller should echo back
	 */
	public RateLimitResult tryConsume(RateLimitPolicy policy, String ipHash) {
		Bucket bucket = buckets.get(key(policy, ipHash), unused -> newBucket(policy));
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			return RateLimitResult.allowed(policy.limit(), probe.getRemainingTokens());
		}
		return RateLimitResult.rejected(policy.limit(), retryAfterSeconds(probe));
	}

	/** Drops all buckets. Used by tests; never called in production code. */
	void reset() {
		buckets.invalidateAll();
	}

	private static String key(RateLimitPolicy policy, String ipHash) {
		return policy.name() + ":" + ipHash;
	}

	private static Bucket newBucket(RateLimitPolicy policy) {
		return Bucket.builder()
				.addLimit(limit -> limit
						.capacity(policy.limit())
						.refillIntervally(policy.limit(), policy.window()))
				.build();
	}

	/** Always at least one second: {@code Retry-After: 0} would invite an immediate retry. */
	private static long retryAfterSeconds(ConsumptionProbe probe) {
		long seconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
		return Math.max(seconds, 1);
	}

	private static Duration longestWindow() {
		Duration longest = Duration.ZERO;
		for (RateLimitPolicy policy : RateLimitPolicy.values()) {
			if (policy.window().compareTo(longest) > 0) {
				longest = policy.window();
			}
		}
		return longest;
	}

}
