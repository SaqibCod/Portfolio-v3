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
	 *
	 * <p>Pinned to HTTP/1.1, which is the one place this client deliberately differs from the
	 * shared one. The JDK client defaults to HTTP/2, and over cleartext {@code http://} that
	 * means every request carries an {@code Upgrade: h2c} offer — which some servers answer by
	 * closing the connection rather than by declining. Against an HTTPS target it costs nothing
	 * either way, because ALPN settles the version during the handshake and never offers h2c.
	 *
	 * <p>That asymmetry matters more here than anywhere else in the application: the other
	 * clients talk to three known APIs, while a probe is pointed at whatever the owner deployed.
	 * A probe that cannot reach a healthy service reports it as down, which is the one failure
	 * mode a status page must not have.
	 */
	@Bean
	public RestClient statusProbeRestClient(StatusProperties statusProperties) {
		return builder(CONNECT_TIMEOUT, statusProperties.timeout(), HttpClient.Version.HTTP_1_1)
				.build();
	}

	/** Package-private so tests can exercise the same wiring with shorter timeouts. */
	static RestClient.Builder builder(Duration connectTimeout, Duration readTimeout) {
		// The JDK default. Every target here is HTTPS, so ALPN negotiates the version and the
		// h2c upgrade that statusProbeRestClient avoids never comes up.
		return builder(connectTimeout, readTimeout, HttpClient.Version.HTTP_2);
	}

	/** Package-private so a test can prove the probe client really speaks HTTP/1.1. */
	static RestClient.Builder builder(Duration connectTimeout, Duration readTimeout,
			HttpClient.Version version) {

		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.followRedirects(HttpClient.Redirect.NEVER)
				.version(version)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().requestFactory(requestFactory);
	}

}
