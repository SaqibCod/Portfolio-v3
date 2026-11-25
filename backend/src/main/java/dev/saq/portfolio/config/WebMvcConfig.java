package dev.saq.portfolio.config;

import dev.saq.portfolio.interceptor.RateLimitInterceptor;
import dev.saq.portfolio.model.GuestbookStatus;
import dev.saq.portfolio.model.StatusWindow;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the rate-limit interceptor across the whole API surface, plus request-parameter types. */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;

	private final AsyncTaskExecutor asyncTaskExecutor;

	/**
	 * @param asyncTaskExecutor qualified by name: {@code taskScheduler} is also an
	 *                          {@code AsyncTaskExecutor}, and handing MVC's async support the
	 *                          scheduler would put streaming responses on the same threads the
	 *                          health probes and retention sweeps run on
	 */
	public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor,
			@Qualifier("applicationAsyncExecutor") AsyncTaskExecutor asyncTaskExecutor) {

		this.rateLimitInterceptor = rateLimitInterceptor;
		this.asyncTaskExecutor = asyncTaskExecutor;
	}

	/**
	 * Hands Spring MVC's async handling the application's virtual-thread executor.
	 *
	 * <p>Without this, streaming a response falls back to a default
	 * {@code SimpleAsyncTaskExecutor} that Spring itself warns at startup is "not suitable for
	 * production use under load" — because that default starts a fresh <em>platform</em> thread
	 * per request. {@code /api/v1/chat} is the only async handler on this API, so the warning
	 * appeared the moment the chatbot streamed its first answer and nowhere before.
	 *
	 * <p>The executor injected here is the same virtual-thread one {@code @Async} uses. The
	 * objection in Spring's warning is to unbounded platform threads, not to the executor type:
	 * a {@code SimpleAsyncTaskExecutor} with virtual threads enabled is the right shape for work
	 * that is almost entirely waiting on a language model, which is what this is.
	 */
	@Override
	public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
		configurer.setTaskExecutor(asyncTaskExecutor);
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
