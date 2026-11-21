package dev.saq.portfolio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} support for the jobs under
 * {@link dev.saq.portfolio.scheduler}.
 *
 * <p>Boot runs scheduled tasks on virtual threads here, because
 * {@code spring.threads.virtual.enabled} is set: a job that waits on GitHub costs no platform
 * thread, so a single-threaded scheduler is enough and no pool needs sizing.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
