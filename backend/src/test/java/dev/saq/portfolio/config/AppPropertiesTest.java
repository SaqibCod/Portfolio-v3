package dev.saq.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The application must refuse to start when a required {@code app.*} value is missing,
 * so misconfiguration is caught at deploy time rather than on the first request.
 */
class AppPropertiesTest {

	private static final String[] COMPLETE_CONFIG = {
			"app.api.base-url=https://api.example.com",
			"app.cors.allowed-origins=http://localhost:3000",
			"app.security.ip-salt=test-ip-salt-value",
			"app.security.admin-token=test-admin-token-0123456789abcdef",
			"app.turnstile.secret-key=1x0000000000000000000000000000000AA",
			"app.mail.resend-api-key=test-resend-api-key",
			"app.mail.from=portfolio@example.com",
			"app.mail.owner-email=owner@example.com"
	};

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(AppPropertiesConfig.class);

	@Test
	void bindsEveryPropertyWhenFullyConfigured() {
		runner.withPropertyValues(COMPLETE_CONFIG).run(context -> {
			assertThat(context).hasNotFailed();
			AppProperties properties = context.getBean(AppProperties.class);
			assertThat(properties.api().baseUrl()).isEqualTo("https://api.example.com");
			assertThat(properties.cors().allowedOrigins()).containsExactly("http://localhost:3000");
			assertThat(properties.security().ipSalt()).isEqualTo("test-ip-salt-value");
			assertThat(properties.security().adminToken()).isEqualTo("test-admin-token-0123456789abcdef");
			assertThat(properties.turnstile().secretKey()).isEqualTo("1x0000000000000000000000000000000AA");
			assertThat(properties.mail().resendApiKey()).isEqualTo("test-resend-api-key");
			assertThat(properties.mail().from()).isEqualTo("portfolio@example.com");
			assertThat(properties.mail().ownerEmail()).isEqualTo("owner@example.com");
		});
	}

	@Test
	void failsWhenNothingIsConfigured() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenAdminTokenIsMissing() {
		runner.withPropertyValues(without("app.security.admin-token"))
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenIpSaltIsMissing() {
		runner.withPropertyValues(without("app.security.ip-salt"))
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenTurnstileSecretIsMissing() {
		runner.withPropertyValues(without("app.turnstile.secret-key"))
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenTheApiBaseUrlIsMissing() {
		runner.withPropertyValues(without("app.api.base-url"))
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenTheApiBaseUrlIsNotAnAbsoluteUrl() {
		runner.withPropertyValues(COMPLETE_CONFIG)
				.withPropertyValues("app.api.base-url=api.example.com")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenAllowedOriginsIsEmpty() {
		runner.withPropertyValues(without("app.cors.allowed-origins"))
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenOwnerEmailIsNotAnEmailAddress() {
		runner.withPropertyValues(COMPLETE_CONFIG)
				.withPropertyValues("app.mail.owner-email=not-an-email")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWhenAdminTokenIsTooShort() {
		runner.withPropertyValues(COMPLETE_CONFIG)
				.withPropertyValues("app.security.admin-token=too-short")
				.run(context -> assertThat(context).hasFailed());
	}

	/** The complete configuration minus one key, so each key can be proven required. */
	private static String[] without(String key) {
		return java.util.Arrays.stream(COMPLETE_CONFIG)
				.filter(entry -> !entry.startsWith(key + "="))
				.toArray(String[]::new);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AppProperties.class)
	static class AppPropertiesConfig {
	}

}
