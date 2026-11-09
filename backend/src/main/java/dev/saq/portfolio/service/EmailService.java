package dev.saq.portfolio.service;

/**
 * Sends plain-text email.
 *
 * <p>Two implementations: {@code ResendEmailService} for deployed environments, and
 * {@code LoggingEmailService} for local development and tests. Sending is fire and forget — a
 * failure must never fail the request that triggered it.
 */
public interface EmailService {

	/**
	 * @param to      recipient address
	 * @param subject subject line
	 * @param text    plain-text body
	 * @param replyTo address to set as {@code Reply-To}, or {@code null} for none
	 */
	void send(String to, String subject, String text, String replyTo);

}
