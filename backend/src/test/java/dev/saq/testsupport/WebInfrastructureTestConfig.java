package dev.saq.testsupport;

import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.exception.ProblemTypes;
import dev.saq.portfolio.service.RateLimiterService;
import dev.saq.portfolio.util.ClientIpUtils;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Supplies the cross-cutting beans an MVC slice needs to behave like the real application.
 *
 * <p>An {@code @WebMvcTest} slice loads every {@code WebMvcConfigurer}, {@code HandlerInterceptor}
 * and {@code Filter}, which here means CORS, the rate-limit interceptor and the admin filter — but
 * it loads no {@code @Service} or {@code @Component} they depend on, and it does not apply
 * {@code @ConfigurationPropertiesScan}. The real implementations are used rather than mocks, so
 * slice tests exercise the actual behaviour.
 *
 * <p>Tests that use this pair their {@code @WebMvcTest} with
 * {@code controllers = { TestEndpointsController.class, TestAdminController.class }}. These slices
 * are about the infrastructure, not about any real endpoint, and a bare {@code @WebMvcTest} scans
 * every {@code @Controller} in the application — so each feature phase would otherwise break them
 * with a controller whose collaborators this configuration knows nothing about.
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

	/**
	 * The executor {@code WebMvcConfig} hands to Spring MVC's async support.
	 *
	 * <p>Needed because an {@code @WebMvcTest} loads every {@code WebMvcConfigurer} but none of
	 * the {@code @Configuration} classes they depend on, so without this the slice fails to start
	 * with "No qualifying bean of type AsyncTaskExecutor" — a startup failure in every controller
	 * test, caused by a change to one of them. Named to match {@code AsyncConfig}'s bean, since
	 * that is the name {@code WebMvcConfig} qualifies on.
	 */
	@Bean(destroyMethod = "close")
	SimpleAsyncTaskExecutor applicationAsyncExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("test-async-");
		executor.setVirtualThreads(true);
		return executor;
	}

}
