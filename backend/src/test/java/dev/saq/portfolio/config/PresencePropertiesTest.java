package dev.saq.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Presence has to bind with nothing configured, because both of its values are ceilings with
 * sensible defaults and a deployment should never have to name one. The rest guards the opposite
 * risk: a value that binds but disables the feature.
 */
class PresencePropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PresencePropertiesConfig.class);

	@Test
	void bindsWithNoPresenceConfigurationAtAll() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			PresenceProperties properties = context.getBean(PresenceProperties.class);

			assertThat(properties.maxSessions()).isEqualTo(500);
			assertThat(properties.staleAfter()).isEqualTo(Duration.ofSeconds(60));
		});
	}

	@Test
	void bindsTheValuesApplicationYmlShips() {
		runner.withPropertyValues(
				"app.presence.max-sessions=500",
				"app.presence.stale-after=60s")
				.run(context -> {
					assertThat(context).hasNotFailed();
					PresenceProperties properties = context.getBean(PresenceProperties.class);

					assertThat(properties.maxSessions()).isEqualTo(500);
					assertThat(properties.staleAfter()).isEqualTo(Duration.ofSeconds(60));
				});
	}

	@Test
	void overridesEachCeilingIndependently() {
		runner.withPropertyValues("app.presence.max-sessions=4").run(context -> {
			PresenceProperties properties = context.getBean(PresenceProperties.class);

			assertThat(properties.maxSessions()).isEqualTo(4);
			// The one that was not named keeps its default rather than becoming zero.
			assertThat(properties.staleAfter()).isEqualTo(Duration.ofSeconds(60));
		});
	}

	@Test
	void refusesASessionCeilingOfZero() {
		// Zero would bind cleanly and silently turn the endpoint off, closing every connection
		// the moment it opened. Failing at startup is the only honest answer.
		runner.withPropertyValues("app.presence.max-sessions=0")
				.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(PresenceProperties.class)
	static class PresencePropertiesConfig {
	}

}
