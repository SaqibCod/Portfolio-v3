package dev.saq.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The status feature has to bind with no configuration at all, because that is the state the
 * site ships in until a project is deployed. Everything else here guards the opposite risk: a
 * service entry that binds but is unusable.
 */
class StatusPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(StatusPropertiesConfig.class);

	@Test
	void startsWithNoStatusConfigurationAtAllAndMonitorsNothing() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			StatusProperties properties = context.getBean(StatusProperties.class);

			assertThat(properties.services()).isEmpty();
			assertThat(properties.hasNoServices()).isTrue();
		});
	}

	@Test
	void fallsBackToTheDocumentedTimings() {
		runner.run(context -> {
			StatusProperties properties = context.getBean(StatusProperties.class);

			assertThat(properties.interval()).isEqualTo(Duration.ofMinutes(5));
			assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(10));
			assertThat(properties.degradedThreshold()).isEqualTo(Duration.ofSeconds(2));
			assertThat(properties.retention()).isEqualTo(Duration.ofDays(35));
		});
	}

	@Test
	void bindsAnExplicitlyEmptyServiceList() {
		// What application.yml actually ships: `services: []` rather than the key being absent.
		runner.withPropertyValues("app.status.services=").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(StatusProperties.class).hasNoServices()).isTrue();
		});
	}

	@Test
	void bindsAServiceAndItsHealthPathDefault() {
		runner.withPropertyValues(
				"app.status.interval=30s",
				"app.status.services[0].slug=githmind",
				"app.status.services[0].name=githMind",
				"app.status.services[0].url=https://githmind.example.com")
				.run(context -> {
					StatusProperties properties = context.getBean(StatusProperties.class);

					assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(30));
					assertThat(properties.hasNoServices()).isFalse();
					assertThat(properties.services()).singleElement().satisfies(service -> {
						assertThat(service.slug()).isEqualTo("githmind");
						assertThat(service.name()).isEqualTo("githMind");
						assertThat(service.url()).isEqualTo("https://githmind.example.com");
						assertThat(service.healthPath()).isEqualTo("/actuator/health");
					});
				});
	}

	@Test
	void findsAServiceBySlugAndReportsNothingForAnUnknownOne() {
		runner.withPropertyValues(
				"app.status.services[0].slug=githmind",
				"app.status.services[0].name=githMind",
				"app.status.services[0].url=https://githmind.example.com")
				.run(context -> {
					StatusProperties properties = context.getBean(StatusProperties.class);

					assertThat(properties.findBySlug("githmind")).isNotNull();
					assertThat(properties.findBySlug("not-monitored")).isNull();
				});
	}

	@Test
	void failsOnASlugTheContractWouldNotAccept() {
		// The contract's serviceSlug pattern is ^[a-z0-9-]+$, and the slug is also what the
		// site puts in a URL, so an uppercase or spaced slug must not reach runtime.
		runner.withPropertyValues(
				"app.status.services[0].slug=githMind",
				"app.status.services[0].name=githMind",
				"app.status.services[0].url=https://githmind.example.com")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsOnAServiceWithNoUrlToProbe() {
		runner.withPropertyValues(
				"app.status.services[0].slug=githmind",
				"app.status.services[0].name=githMind")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void joinsTheUrlAndHealthPathWithExactlyOneSlash() {
		StatusProperties.Service trailing = new StatusProperties.Service(
				"githmind", "githMind", "https://githmind.example.com/", "/actuator/health");
		StatusProperties.Service neither = new StatusProperties.Service(
				"githmind", "githMind", "https://githmind.example.com", "actuator/health");

		assertThat(trailing.probeUrl())
				.isEqualTo("https://githmind.example.com/actuator/health");
		assertThat(neither.probeUrl())
				.isEqualTo("https://githmind.example.com/actuator/health");
	}

	@Test
	void holdsAnUnmodifiableCopyOfTheServiceList() {
		java.util.List<StatusProperties.Service> mutable = new java.util.ArrayList<>();
		mutable.add(new StatusProperties.Service("a", "A", "https://a.example.com", "/health"));

		StatusProperties properties = new StatusProperties(Duration.ofMinutes(5),
				Duration.ofSeconds(10), Duration.ofSeconds(2), Duration.ofDays(35), mutable);
		mutable.clear();

		assertThat(properties.services()).hasSize(1);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(StatusProperties.class)
	static class StatusPropertiesConfig {
	}

}
