package dev.saq.portfolio.service;

import dev.saq.portfolio.client.TurnstileClient;
import dev.saq.portfolio.dto.external.TurnstileVerifyResponse;
import dev.saq.portfolio.exception.TurnstileFailedException;
import dev.saq.portfolio.exception.UpstreamUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Decides whether a Turnstile token may be trusted.
 *
 * <p>Fails closed: a rejected token is a 422, and an unreachable Cloudflare is a 503 rather than
 * a free pass, so an outage cannot be used to bypass spam protection.
 */
@Service
public class TurnstileService {

	private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);

	private final TurnstileClient turnstileClient;

	public TurnstileService(TurnstileClient turnstileClient) {
		this.turnstileClient = turnstileClient;
	}

	/**
	 * @param token    the token submitted with the form
	 * @param remoteIp the caller's IP, or {@code null}
	 * @throws TurnstileFailedException      if the token is missing or rejected
	 * @throws UpstreamUnavailableException  if Cloudflare cannot be reached
	 */
	public void verify(String token, String remoteIp) {
		if (token == null || token.isBlank()) {
			throw new TurnstileFailedException("Turnstile token is missing.");
		}

		TurnstileVerifyResponse response;
		try {
			response = turnstileClient.verify(token, remoteIp);
		}
		catch (RestClientException ex) {
			throw new UpstreamUnavailableException("Turnstile verification is unavailable.", ex);
		}

		if (response == null || !response.success()) {
			// Cloudflare's codes are safe to log; the token itself is not logged.
			log.info("Turnstile rejected a submission: {}",
					(response != null) ? response.errorCodes() : "no response body");
			throw new TurnstileFailedException("Turnstile verification failed.");
		}
	}

}
