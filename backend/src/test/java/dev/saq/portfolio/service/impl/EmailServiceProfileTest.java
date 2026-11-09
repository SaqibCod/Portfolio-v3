package dev.saq.portfolio.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.saq.portfolio.client.ResendClient;
import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.service.EmailService;
import dev.saq.testsupport.TestAppProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Exactly one {@link EmailService} must be active in every environment, and never the sending one
 * locally or in tests. The {@code @Profile} expressions are easy to get subtly wrong, so they are
 * pinned here.
 */
class EmailServiceProfileTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(EmailBeans.class, LoggingEmailService.class, ResendEmailService.class);

	@Test
	void logsInsteadOfSendingUnderTheTestProfile() {
		runner.withPropertyValues("spring.profiles.active=test")
				.run(context -> assertThat(context).hasSingleBean(EmailService.class)
						.getBean(EmailService.class).isInstanceOf(LoggingEmailService.class));
	}

	@Test
	void logsInsteadOfSendingUnderTheLocalProfile() {
		runner.withPropertyValues("spring.profiles.active=local")
				.run(context -> assertThat(context).hasSingleBean(EmailService.class)
						.getBean(EmailService.class).isInstanceOf(LoggingEmailService.class));
	}

	@Test
	void sendsThroughResendInADeployedEnvironment() {
		runner.withPropertyValues("spring.profiles.active=prod")
				.run(context -> assertThat(context).hasSingleBean(EmailService.class)
						.getBean(EmailService.class).isInstanceOf(ResendEmailService.class));
	}

	@Test
	void sendsThroughResendWhenNoProfileIsSet() {
		runner.run(context -> assertThat(context).hasSingleBean(EmailService.class)
				.getBean(EmailService.class).isInstanceOf(ResendEmailService.class));
	}

	@Configuration(proxyBeanMethods = false)
	static class EmailBeans {

		@Bean
		AppProperties appProperties() {
			return TestAppProperties.sample();
		}

		@Bean
		ResendClient resendClient(AppProperties properties) {
			return new ResendClient(RestClient.builder(), properties);
		}

	}

}
