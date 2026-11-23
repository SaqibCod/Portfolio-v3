package dev.saq.portfolio.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.repository.StatusCheckRepository;
import dev.saq.portfolio.service.HealthProbeService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * That Spring can actually build the two status jobs.
 *
 * <p>This exists because of a hole the rest of the suite cannot cover. Both jobs are
 * {@code @Profile("!test")}, so no integration test ever instantiates them; and both take a
 * second, package-private constructor for a fixed {@link java.time.Clock}, which leaves Spring
 * with two candidates and no way to choose unless one carries {@code @Autowired}. A missing
 * annotation therefore compiles, passes every unit test, and fails at startup — which is exactly
 * what happened once.
 *
 * <p>So this registers them against a real container, which is the only thing that checks the
 * annotation is there. Their collaborators are mocked; nothing here is about behaviour.
 */
class SchedulerWiringTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(StatusProperties.class, SchedulerWiringTest::properties)
			.withBean(StatusCheckRepository.class, () -> mock(StatusCheckRepository.class))
			.withBean(HealthProbeService.class, () -> mock(HealthProbeService.class));

	@Test
	void theProbeSweepIsConstructible() {
		runner.withBean(HealthProbeScheduler.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(HealthProbeScheduler.class);
		});
	}

	@Test
	void theRetentionSweepIsConstructible() {
		runner.withBean(StatusRetentionScheduler.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(StatusRetentionScheduler.class);
		});
	}

	@Test
	void bothJobsCanLiveInOneContext() {
		runner.withBean(HealthProbeScheduler.class)
				.withBean(StatusRetentionScheduler.class)
				.run(context -> assertThat(context).hasNotFailed());
	}

	private static StatusProperties properties() {
		return new StatusProperties(Duration.ofMinutes(5), Duration.ofSeconds(10),
				Duration.ofSeconds(2), Duration.ofDays(35), List.of());
	}

}
