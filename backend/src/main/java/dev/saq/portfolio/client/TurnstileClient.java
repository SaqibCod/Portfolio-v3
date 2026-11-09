package dev.saq.portfolio.client;

import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.dto.external.TurnstileVerifyResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over Cloudflare's Turnstile {@code siteverify} endpoint.
 *
 * <p>Transport only: it performs the call and returns Cloudflare's answer. Deciding what a failed
 * verification means is {@link dev.saq.portfolio.service.TurnstileService}'s job.
 */
@Component
public class TurnstileClient {

	static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	private final RestClient restClient;

	private final String secretKey;

	public TurnstileClient(RestClient.Builder restClientBuilder, AppProperties properties) {
		this.restClient = restClientBuilder.build();
		this.secretKey = properties.turnstile().secretKey();
	}

	/**
	 * Verifies a widget token with Cloudflare.
	 *
	 * @param token    the token the widget produced
	 * @param remoteIp the caller's IP, passed through for Cloudflare's own risk scoring; optional
	 * @throws org.springframework.web.client.RestClientException if Cloudflare cannot be reached
	 */
	public TurnstileVerifyResponse verify(String token, String remoteIp) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("secret", secretKey);
		form.add("response", token);
		if (remoteIp != null && !remoteIp.isBlank()) {
			form.add("remoteip", remoteIp);
		}

		return restClient.post()
				.uri(VERIFY_URL)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TurnstileVerifyResponse.class);
	}

}
