package dev.saq.portfolio.config;

import dev.saq.portfolio.interceptor.RateLimitInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the rate-limit interceptor across the whole API surface. */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;

	public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
		this.rateLimitInterceptor = rateLimitInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Registered API-wide; the interceptor itself only acts on @RateLimited handlers.
		registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
	}

}
