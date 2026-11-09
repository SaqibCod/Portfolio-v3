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
