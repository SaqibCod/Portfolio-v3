package dev.saq.portfolio.service;

import java.util.concurrent.CompletableFuture;

/**
 * Sends plain-text email.
 *
 * <p>Two implementations: {@code ResendEmailService} for deployed environments, and
 * {@code LoggingEmailService} for local development and tests.
 *
 * <p>Sending is asynchronous and never throws at the call site — a failure must not fail the
 * request that triggered it, which has usually been answered already. The returned future is how
 * the outcome is still observable: it completes normally once the provider has accepted the
 * message, and completes exceptionally when it has not. Callers that record a delivery status
 * (see {@code ContactService}) attach a completion callback; callers that genuinely do not care
 * may ignore the result.
 */
public interface EmailService {

	/**
	 * @param to      recipient address
	 * @param subject subject line
	 * @param text    plain-text body
	 * @param replyTo address to set as {@code Reply-To}, or {@code null} for none
	 * @return a future completing when the provider has accepted the message, or completing
	 *         exceptionally if it rejected it or could not be reached
	 */
	CompletableFuture<Void> send(String to, String subject, String text, String replyTo);

}
