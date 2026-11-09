package dev.saq.portfolio.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Runs {@code @Async} work (currently: sending email) on virtual threads, so fire-and-forget
 * tasks never block a request and need no bounded pool.
 */
// proxyBeanMethods stays on: getAsyncExecutor() calls the @Bean method, and must get the managed
// singleton back rather than a second, unmanaged executor.
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

	/** Matches {@code server.shutdown: graceful}: in-flight sends get this long to finish. */
	private static final long TASK_TERMINATION_TIMEOUT_MS = 10_000;

	/**
	 * Declared as a bean so the container owns its lifecycle and closes it on shutdown, which is
	 * what gives in-flight tasks a chance to complete during a graceful stop.
	 */
	@Bean(destroyMethod = "close")
	public SimpleAsyncTaskExecutor applicationAsyncExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("async-");
		executor.setVirtualThreads(true);
		executor.setTaskTerminationTimeout(TASK_TERMINATION_TIMEOUT_MS);
		return executor;
	}

	@Override
	public Executor getAsyncExecutor() {
		return applicationAsyncExecutor();
	}

}
