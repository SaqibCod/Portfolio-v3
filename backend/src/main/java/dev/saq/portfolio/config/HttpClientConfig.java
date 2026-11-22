package dev.saq.portfolio.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The single {@link RestClient.Builder} every outbound HTTP client is built from.
 *
 * <p>Timeouts are explicit so a slow third party (GitHub, Cloudflare, Resend) can never pin a
 * request thread indefinitely. Declaring this bean replaces Boot's auto-configured builder, which
 * is conditional on one not already being defined.
 */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Prototype-scoped, like the auto-configured builder it replaces: a builder is mutable, so a
	 * shared instance would let one client's base URL and auth header leak into the next.
	 */
	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public RestClient.Builder restClientBuilder() {
		return builder(CONNECT_TIMEOUT, READ_TIMEOUT);
	}

	/**
	 * The client health probes are made with, on {@code app.status.timeout} rather than the
	 * shared read timeout.
	 *
	 * <p>A probe's timeout is not an infrastructure detail but part of what the status page
	 * means: it is the line between "slow" and "down", and the owner sets it per deployment. The
	 * shared ten seconds would quietly override that for anything configured to be stricter.
	 *
	 * <p>Redirects are not followed, inherited from {@link #builder}. A health endpoint that
	 * answers {@code 302} has not reported itself healthy, and treating the redirect target's
	 * answer as the service's own would measure whatever happens to sit behind it.
	 */
	@Bean
	public RestClient statusProbeRestClient(StatusProperties statusProperties) {
		return builder(CONNECT_TIMEOUT, statusProperties.timeout()).build();
	}

	/** Package-private so tests can exercise the same wiring with shorter timeouts. */
	static RestClient.Builder builder(Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().requestFactory(requestFactory);
	}

}
