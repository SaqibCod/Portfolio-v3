package dev.saq.portfolio.service;

import java.util.UUID;

import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.dto.request.ContactRequest;
import dev.saq.portfolio.dto.response.ContactAcceptedResponse;
import dev.saq.portfolio.mapper.ContactMapper;
import dev.saq.portfolio.model.ContactMessage;
import dev.saq.portfolio.model.DeliveryStatus;
import dev.saq.portfolio.repository.ContactMessageRepository;
import dev.saq.portfolio.util.ClientIpUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Accepts a contact-form submission: screens it, stores it, and has it emailed to the owner.
 *
 * <p>The order is deliberate. The honeypot is checked before anything else, so an obvious bot
 * costs one comparison and leaves no trace. Turnstile is checked before the insert, so a rejected
 * submission never reaches the table. Only then is the message stored and handed to the mail
 * provider.
 *
 * <p>No {@code @Transactional}: the insert is a single statement that commits on its own, and the
 * delivery callback below runs on another thread. Wrapping both in one transaction would let the
 * callback's {@code update} run before the insert had committed and silently match no rows.
 */
@Service
public class ContactService {

	private static final Logger log = LoggerFactory.getLogger(ContactService.class);

	private static final String SUBJECT_PREFIX = "[Portfolio] ";

	private final ContactMessageRepository contactMessageRepository;

	private final TurnstileService turnstileService;

	private final EmailService emailService;

	private final ContactMapper contactMapper;

	private final ClientIpUtils clientIpUtils;

	private final String ownerEmail;

	public ContactService(ContactMessageRepository contactMessageRepository,
			TurnstileService turnstileService, EmailService emailService, ContactMapper contactMapper,
			ClientIpUtils clientIpUtils, AppProperties properties) {
		this.contactMessageRepository = contactMessageRepository;
		this.turnstileService = turnstileService;
		this.emailService = emailService;
		this.contactMapper = contactMapper;
		this.clientIpUtils = clientIpUtils;
		this.ownerEmail = properties.mail().ownerEmail();
	}

	/**
	 * Takes one submission.
	 *
	 * @param request  the validated submission
	 * @param clientIp the caller's address, or {@code null} if the container could not supply one.
	 *                 Used for Cloudflare's risk scoring and, hashed, as the stored fingerprint —
	 *                 the raw value is never persisted.
	 * @return the accepted response, always with status {@code queued}
	 * @throws dev.saq.portfolio.exception.TurnstileFailedException     if the token is rejected (422)
	 * @throws dev.saq.portfolio.exception.UpstreamUnavailableException if Cloudflare is unreachable (503)
	 */
	public ContactAcceptedResponse submit(ContactRequest request, String clientIp) {
		if (request.isHoneypotTripped()) {
			// Answered exactly like a real submission, down to the shape of the id: a bot that
			// could tell the difference would simply stop filling the field in.
			log.info("Dropped a contact submission that filled in the honeypot.");
			return ContactAcceptedResponse.queued(UUID.randomUUID());
		}

		turnstileService.verify(request.turnstileToken(), clientIp);

		ContactMessage stored = contactMessageRepository
				.save(contactMapper.toEntity(request, clientIpUtils.hash(clientIp)));
		deliver(stored);

		return contactMapper.toResponse(stored);
	}

	/**
	 * Hands the message to the mail provider and records what came back.
	 *
	 * <p>Returns immediately: {@link EmailService} is asynchronous, so the visitor is answered
	 * without waiting for the provider, and the row is corrected to {@code SENT} or
	 * {@code FAILED} whenever it answers.
	 */
	private void deliver(ContactMessage message) {
		emailService
				.send(ownerEmail, subjectFor(message), bodyFor(message), message.email())
				.whenComplete((ignored, failure) -> recordOutcome(message.id(), failure));
	}

	private void recordOutcome(UUID id, Throwable failure) {
		DeliveryStatus status = (failure == null) ? DeliveryStatus.SENT : DeliveryStatus.FAILED;
		try {
			if (contactMessageRepository.updateStatus(id, status) == 0) {
				log.warn("Contact message {} vanished before its delivery status could be recorded", id);
			}
		}
		catch (DataAccessException ex) {
			// Nothing above this callback can act on the failure; losing the status is survivable,
			// losing the log entry is not.
			log.error("Could not record delivery status {} for contact message {}", status, id, ex);
		}
	}

	/** {@code [Portfolio] <subject, or the sender's name when there is none>}. */
	private static String subjectFor(ContactMessage message) {
		String summary = (message.subject() != null) ? message.subject() : message.name();
		return SUBJECT_PREFIX + singleLine(summary);
	}

	private static String bodyFor(ContactMessage message) {
		StringBuilder body = new StringBuilder("New message from the portfolio contact form.\n\n")
				.append("From:    ").append(message.name()).append("\n")
				.append("Email:   ").append(message.email()).append("\n");
		if (message.subject() != null) {
			body.append("Subject: ").append(message.subject()).append("\n");
		}
		return body.append("\n").append(message.message()).append("\n").toString();
	}

	/**
	 * Flattens anything that could break a header out of a subject line.
	 *
	 * <p>The provider is given JSON rather than a raw MIME message, so a newline here cannot
	 * inject a header today — but a subject is header data, and keeping it on one line means that
	 * stays true if the transport ever changes.
	 */
	private static String singleLine(String value) {
		return value.replaceAll("[\\r\\n]+", " ").strip();
	}

}
