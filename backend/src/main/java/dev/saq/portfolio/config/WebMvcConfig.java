package dev.saq.portfolio.config;

import dev.saq.portfolio.interceptor.RateLimitInterceptor;
import dev.saq.portfolio.model.GuestbookStatus;
import dev.saq.portfolio.model.StatusWindow;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the rate-limit interceptor across the whole API surface, plus request-parameter types. */
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

	/**
	 * Binds the API's lowercase status values to {@link GuestbookStatus}.
	 *
	 * <p>Spring's default enum converter calls {@code Enum.valueOf}, which is case-sensitive and
	 * would reject the contract's {@code pending} outright. Delegating to
	 * {@link GuestbookStatus#fromApi} instead keeps the lowercase mapping in the enum — the one
	 * place that owns it — and turns an unknown value into a type-mismatch, which the framework
	 * reports as a 400 rather than letting it surface as an unhandled 500.
	 *
	 * <p>{@link StatusWindow} is registered for the same reason: {@code 24h} is not a legal Java
	 * constant name, so there is no spelling of the enum that {@code Enum.valueOf} could match.
	 */
	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(String.class, GuestbookStatus.class, GuestbookStatus::fromApi);
		registry.addConverter(String.class, StatusWindow.class, StatusWindow::fromApi);
	}

}
