package dev.saq.testsupport;

import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.exception.ProblemTypes;
import dev.saq.portfolio.service.RateLimiterService;
import dev.saq.portfolio.util.ClientIpUtils;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the cross-cutting beans an MVC slice needs to behave like the real application.
 *
 * <p>An {@code @WebMvcTest} slice loads every {@code WebMvcConfigurer}, {@code HandlerInterceptor}
 * and {@code Filter}, which here means CORS, the rate-limit interceptor and the admin filter — but
 * it loads no {@code @Service} or {@code @Component} they depend on, and it does not apply
 * {@code @ConfigurationPropertiesScan}. The real implementations are used rather than mocks, so
 * slice tests exercise the actual behaviour.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class WebInfrastructureTestConfig {

	@Bean
	ProblemTypes problemTypes(AppProperties properties) {
		return new ProblemTypes(properties);
	}

	@Bean
	RateLimiterService rateLimiterService() {
		return new RateLimiterService();
	}

	@Bean
	ClientIpUtils clientIpUtils(AppProperties properties) {
		return new ClientIpUtils(properties);
	}

}
