package dev.saq.portfolio.service.impl;

import dev.saq.portfolio.service.EmailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Logs mail instead of sending it, so local development and tests need no Resend key and can
 * never email a real person by accident.
 */
@Service
@Profile({ "local", "test" })
public class LoggingEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

	@Override
	public void send(String to, String subject, String text, String replyTo) {
		log.info("""
				Email not sent (logging email service active)
				  to:       {}
				  subject:  {}
				  replyTo:  {}
				  text:     {}""", to, subject, replyTo, text);
	}

}
