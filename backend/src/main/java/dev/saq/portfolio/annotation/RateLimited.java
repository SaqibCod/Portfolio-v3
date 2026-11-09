package dev.saq.portfolio.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.saq.portfolio.model.RateLimitPolicy;

/**
 * Applies a rate-limit policy to a controller method (or to every method of a controller).
 *
 * <p>Enforced by {@link dev.saq.portfolio.interceptor.RateLimitInterceptor}: the request is
 * counted against the policy for the caller's hashed IP, successful responses carry
 * {@code RateLimit-Limit} and {@code RateLimit-Remaining}, and exceeding the budget produces a
 * 429 with {@code Retry-After}.
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

	/** The budget to charge this request against. */
	RateLimitPolicy value();

}
