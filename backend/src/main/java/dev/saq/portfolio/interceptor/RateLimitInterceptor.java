package dev.saq.portfolio.interceptor;

import dev.saq.portfolio.annotation.RateLimited;
import dev.saq.portfolio.dto.internal.RateLimitResult;
import dev.saq.portfolio.exception.RateLimitedException;
import dev.saq.portfolio.service.RateLimiterService;
import dev.saq.portfolio.util.ClientIpUtils;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces {@link RateLimited} before a handler runs.
 *
 * <p>Allowed requests get {@code RateLimit-Limit} and {@code RateLimit-Remaining}; a request over
 * budget raises {@link RateLimitedException}, which
 * {@link dev.saq.portfolio.exception.GlobalExceptionHandler} turns into a 429 with
 * {@code Retry-After}. Handlers without the annotation are not touched.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	static final String LIMIT_HEADER = "RateLimit-Limit";

	static final String REMAINING_HEADER = "RateLimit-Remaining";

	private final RateLimiterService rateLimiterService;

	private final ClientIpUtils clientIpUtils;

	public RateLimitInterceptor(RateLimiterService rateLimiterService, ClientIpUtils clientIpUtils) {
		this.rateLimiterService = rateLimiterService;
		this.clientIpUtils = clientIpUtils;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		RateLimited rateLimited = findAnnotation(handler);
		if (rateLimited == null) {
			return true;
		}

		RateLimitResult result = rateLimiterService.tryConsume(rateLimited.value(),
				clientIpUtils.hashOf(request));
		response.setHeader(LIMIT_HEADER, Long.toString(result.limit()));
		response.setHeader(REMAINING_HEADER, Long.toString(result.remaining()));
		if (!result.allowed()) {
			throw new RateLimitedException("Rate limit exceeded. Try again later.",
					result.retryAfterSeconds());
		}
		return true;
	}

	/** Method-level annotation wins; otherwise the controller-level one applies. */
	private static RateLimited findAnnotation(Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return null;
		}
		RateLimited onMethod = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
				RateLimited.class);
		return (onMethod != null) ? onMethod
				: AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RateLimited.class);
	}

}
