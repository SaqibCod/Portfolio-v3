package dev.saq.portfolio.client;

import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.dto.external.ResendEmailRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over Resend's email API. Transport only: no retry or fallback policy lives here.
 */
@Component
public class ResendClient {

	static final String EMAILS_URL = "https://api.resend.com/emails";

	private final RestClient restClient;

	public ResendClient(RestClient.Builder restClientBuilder, AppProperties properties) {
		this.restClient = restClientBuilder
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.mail().resendApiKey())
				.build();
	}

	/**
	 * Hands one message to Resend.
	 *
	 * @throws org.springframework.web.client.RestClientException if Resend rejects it or cannot be
	 *                                                            reached
	 */
	public void send(ResendEmailRequest request) {
		restClient.post()
				.uri(EMAILS_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toBodilessEntity();
	}

}
