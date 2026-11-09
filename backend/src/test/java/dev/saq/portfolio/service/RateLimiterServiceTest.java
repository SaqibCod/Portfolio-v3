package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.saq.portfolio.dto.internal.RateLimitResult;
import dev.saq.portfolio.model.RateLimitPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RateLimiterServiceTest {

	private final RateLimiterService rateLimiterService = new RateLimiterService();

	@Test
	void allowsRequestsUpToTheLimitAndCountsDown() {
		long limit = RateLimitPolicy.GUESTBOOK.limit();

		for (long request = 1; request <= limit; request++) {
			RateLimitResult result = rateLimiterService.tryConsume(RateLimitPolicy.GUESTBOOK, "ip-hash-a");

			assertThat(result.allowed()).isTrue();
			assertThat(result.limit()).isEqualTo(limit);
			assertThat(result.remaining()).isEqualTo(limit - request);
			assertThat(result.retryAfterSeconds()).isZero();
		}
	}

	@Test
	void rejectsTheRequestAfterTheLimitIsReached() {
		exhaust(RateLimitPolicy.GUESTBOOK, "ip-hash-b");

		RateLimitResult result = rateLimiterService.tryConsume(RateLimitPolicy.GUESTBOOK, "ip-hash-b");

		assertThat(result.allowed()).isFalse();
		assertThat(result.limit()).isEqualTo(RateLimitPolicy.GUESTBOOK.limit());
		assertThat(result.remaining()).isZero();
		assertThat(result.retryAfterSeconds())
				.isPositive()
				.isLessThanOrEqualTo(RateLimitPolicy.GUESTBOOK.window().toSeconds());
	}

	@Test
	void budgetsAreTrackedPerCaller() {
		exhaust(RateLimitPolicy.GUESTBOOK, "ip-hash-c");

		assertThat(rateLimiterService.tryConsume(RateLimitPolicy.GUESTBOOK, "ip-hash-c").allowed()).isFalse();
		assertThat(rateLimiterService.tryConsume(RateLimitPolicy.GUESTBOOK, "ip-hash-d").allowed()).isTrue();
	}

	@Test
	void budgetsAreTrackedPerPolicy() {
		exhaust(RateLimitPolicy.GUESTBOOK, "ip-hash-e");

		assertThat(rateLimiterService.tryConsume(RateLimitPolicy.GUESTBOOK, "ip-hash-e").allowed()).isFalse();
		assertThat(rateLimiterService.tryConsume(RateLimitPolicy.CONTACT, "ip-hash-e").allowed()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(RateLimitPolicy.class)
	void everyPolicyReportsItsOwnLimit(RateLimitPolicy policy) {
		RateLimitResult result = rateLimiterService.tryConsume(policy, "ip-hash-" + policy.name());

		assertThat(result.allowed()).isTrue();
		assertThat(result.limit()).isEqualTo(policy.limit());
		assertThat(result.remaining()).isEqualTo(policy.limit() - 1);
	}

	@Test
	void policiesMatchThePlan() {
		assertThat(RateLimitPolicy.CHAT.limit()).isEqualTo(20);
		assertThat(RateLimitPolicy.CHAT.window().toHours()).isEqualTo(1);
		assertThat(RateLimitPolicy.CONTACT.limit()).isEqualTo(3);
		assertThat(RateLimitPolicy.CONTACT.window().toHours()).isEqualTo(1);
		assertThat(RateLimitPolicy.GUESTBOOK.limit()).isEqualTo(2);
		assertThat(RateLimitPolicy.GUESTBOOK.window().toDays()).isEqualTo(1);
		assertThat(RateLimitPolicy.PUBLIC_READ.limit()).isEqualTo(120);
		assertThat(RateLimitPolicy.PUBLIC_READ.window().toMinutes()).isEqualTo(1);
	}

	@Test
	void resetClearsEveryBucket() {
		exhaust(RateLimitPolicy.GUESTBOOK, "ip-hash-f");

		rateLimiterService.reset();

		assertThat(rateLimiterService.tryConsume(RateLimitPolicy.GUESTBOOK, "ip-hash-f").allowed()).isTrue();
	}

	private void exhaust(RateLimitPolicy policy, String ipHash) {
		for (long request = 0; request < policy.limit(); request++) {
			rateLimiterService.tryConsume(policy, ipHash);
		}
	}

}
