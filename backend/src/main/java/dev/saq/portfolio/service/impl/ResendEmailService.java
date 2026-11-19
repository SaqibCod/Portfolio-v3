package dev.saq.portfolio.service.impl;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.saq.portfolio.client.ResendClient;
import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.dto.external.ResendEmailRequest;
import dev.saq.portfolio.service.EmailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Sends mail through Resend in any deployed environment.
 *
 * <p>Runs on a virtual thread via {@code @Async} so a slow provider never delays the response.
 * An upstream failure is logged and returned as an exceptionally completed future rather than
 * thrown: by the time this runs the caller has already been answered, so there is nobody left to
 * throw to — but a caller recording a delivery status still needs to know it failed.
 */
@Service
@Profile("!local & !test")
public class ResendEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

	private final ResendClient resendClient;

	private final String from;

	public ResendEmailService(ResendClient resendClient, AppProperties properties) {
		this.resendClient = resendClient;
		this.from = properties.mail().from();
	}

	@Async
	@Override
	public CompletableFuture<Void> send(String to, String subject, String text, String replyTo) {
		try {
			resendClient.send(new ResendEmailRequest(from, List.of(to), subject, text, replyTo));
			log.info("Sent email with subject '{}'", subject);
			return CompletableFuture.completedFuture(null);
		}
		catch (RestClientException ex) {
			// Recipient and subject only: the body can contain whatever a visitor typed.
			log.error("Failed to send email to {} with subject '{}'", to, subject, ex);
			return CompletableFuture.failedFuture(ex);
		}
	}

}
