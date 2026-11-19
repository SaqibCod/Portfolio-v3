package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import dev.saq.portfolio.dto.request.ContactRequest;
import dev.saq.portfolio.dto.response.ContactAcceptedResponse;
import dev.saq.portfolio.exception.TurnstileFailedException;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.mapper.ContactMapper;
import dev.saq.portfolio.model.ContactMessage;
import dev.saq.portfolio.model.DeliveryStatus;
import dev.saq.portfolio.repository.ContactMessageRepository;
import dev.saq.portfolio.util.ClientIpUtils;
import dev.saq.testsupport.TestAppProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The screening order, what gets stored, and how a delivery outcome is recorded.
 *
 * <p>The mapper and the IP hasher are the real implementations: both are pure, and using them
 * means the assertions about what reaches the database are about real values rather than stubs.
 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

	private static final String CLIENT_IP = "198.51.100.17";

	@Mock
	private ContactMessageRepository contactMessageRepository;

	@Mock
	private TurnstileService turnstileService;

	@Mock
	private EmailService emailService;

	private ClientIpUtils clientIpUtils;

	private ContactService contactService;

	@BeforeEach
	void setUp() {
		clientIpUtils = new ClientIpUtils(TestAppProperties.sample());
		contactService = new ContactService(contactMessageRepository, turnstileService, emailService,
				new ContactMapper(), clientIpUtils, TestAppProperties.sample());
	}

	@Test
	void storesTheMessageAndAnswersQueued() {
		UUID id = givenTheMessageIsStored();
		givenEmailSucceeds();

		ContactAcceptedResponse response = contactService.submit(request(), CLIENT_IP);

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.status()).isEqualTo("queued");

		ContactMessage stored = capturedMessage();
		assertThat(stored.name()).isEqualTo("Ada Lovelace");
		assertThat(stored.email()).isEqualTo("ada@example.com");
		assertThat(stored.subject()).isEqualTo("A role for you");
		assertThat(stored.message()).isEqualTo("I would like to talk to you about a backend role.");
		assertThat(stored.status()).isEqualTo(DeliveryStatus.QUEUED);
	}

	@Test
	void verifiesTurnstileBeforeStoringAnything() {
		willThrow(new TurnstileFailedException("Turnstile verification failed."))
				.given(turnstileService).verify(anyString(), anyString());

		assertThatExceptionOfType(TurnstileFailedException.class)
				.isThrownBy(() -> contactService.submit(request(), CLIENT_IP));

		verifyNoInteractions(contactMessageRepository, emailService);
	}

	@Test
	void storesNothingWhenCloudflareCannotBeReached() {
		willThrow(new UpstreamUnavailableException("Turnstile verification is unavailable.",
				new IllegalStateException("boom")))
				.given(turnstileService).verify(anyString(), anyString());

		assertThatExceptionOfType(UpstreamUnavailableException.class)
				.isThrownBy(() -> contactService.submit(request(), CLIENT_IP));

		verifyNoInteractions(contactMessageRepository, emailService);
	}

	@Test
	void storesOnlyAHashedAddress() {
		givenTheMessageIsStored();
		givenEmailSucceeds();

		contactService.submit(request(), CLIENT_IP);

		String ipHash = capturedMessage().ipHash();
		assertThat(ipHash)
				.doesNotContain(CLIENT_IP)
				.hasSize(64)
				.matches("[0-9a-f]{64}")
				.isEqualTo(clientIpUtils.hash(CLIENT_IP));
	}

	@Test
	void hashesTheUnknownCallerWhenThereIsNoAddress() {
		givenTheMessageIsStored();
		givenEmailSucceeds();

		contactService.submit(request(), null);

		assertThat(capturedMessage().ipHash()).isEqualTo(clientIpUtils.hash(null));
		then(turnstileService).should().verify("token-from-the-widget", null);
	}

	@Test
	void emailsTheOwnerWithTheVisitorAsReplyTo() {
		givenTheMessageIsStored();
		givenEmailSucceeds();

		contactService.submit(request(), CLIENT_IP);

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		then(emailService).should().send(eq(TestAppProperties.OWNER_EMAIL), subject.capture(),
				body.capture(), eq("ada@example.com"));

		assertThat(subject.getValue()).isEqualTo("[Portfolio] A role for you");
		assertThat(body.getValue())
				.contains("Ada Lovelace")
				.contains("ada@example.com")
				.contains("A role for you")
				.contains("I would like to talk to you about a backend role.");
	}

	@Test
	void fallsBackToTheSendersNameWhenThereIsNoSubject() {
		givenTheMessageIsStored();
		givenEmailSucceeds();

		contactService.submit(request(null), CLIENT_IP);

		then(emailService).should().send(anyString(), eq("[Portfolio] Ada Lovelace"), anyString(),
				anyString());
	}

	@Test
	void keepsTheSubjectOnOneLine() {
		givenTheMessageIsStored();
		givenEmailSucceeds();

		contactService.submit(request("Hello\r\nBcc: victim@example.com"), CLIENT_IP);

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		then(emailService).should().send(anyString(), subject.capture(), anyString(), anyString());

		assertThat(subject.getValue())
				.doesNotContain("\n")
				.doesNotContain("\r")
				.isEqualTo("[Portfolio] Hello Bcc: victim@example.com");
	}

	@Test
	void marksTheMessageSentOnceTheProviderAcceptsIt() {
		UUID id = givenTheMessageIsStored();
		givenEmailSucceeds();

		contactService.submit(request(), CLIENT_IP);

		then(contactMessageRepository).should().updateStatus(id, DeliveryStatus.SENT);
	}

	@Test
	void marksTheMessageFailedWhenDeliveryFails() {
		UUID id = givenTheMessageIsStored();
		given(emailService.send(anyString(), anyString(), anyString(), anyString()))
				.willReturn(CompletableFuture.failedFuture(new IllegalStateException("provider down")));
		given(contactMessageRepository.updateStatus(id, DeliveryStatus.FAILED)).willReturn(1);

		ContactAcceptedResponse response = contactService.submit(request(), CLIENT_IP);

		// The visitor is still told the message was queued: it was stored, and a retry would only
		// duplicate it.
		assertThat(response.status()).isEqualTo("queued");
		then(contactMessageRepository).should().updateStatus(id, DeliveryStatus.FAILED);
	}

	@Test
	void survivesAFailureToRecordTheDeliveryStatus() {
		UUID id = givenTheMessageIsStored();
		givenEmailSucceeds();
		given(contactMessageRepository.updateStatus(id, DeliveryStatus.SENT))
				.willThrow(new DataAccessResourceFailureException("database gone"));

		// The callback runs after the visitor has been answered, so it must never propagate.
		assertThat(contactService.submit(request(), CLIENT_IP).status()).isEqualTo("queued");
	}

	@Test
	void dropsASubmissionThatFilledInTheHoneypot() {
		ContactRequest trapped = new ContactRequest("Spam Bot", "bot@example.com", "Cheap pills",
				"Buy our excellent product today, friend.", "token-from-the-widget",
				"https://spam.example");

		ContactAcceptedResponse response = contactService.submit(trapped, CLIENT_IP);

		// Indistinguishable from a real acceptance, so a bot learns nothing.
		assertThat(response.id()).isNotNull();
		assertThat(response.status()).isEqualTo("queued");
		verifyNoInteractions(contactMessageRepository, emailService, turnstileService);
	}

	@Test
	void treatsAWhitespaceOnlyHoneypotAsEmpty() {
		givenTheMessageIsStored();
		givenEmailSucceeds();

		ContactRequest spaced = new ContactRequest("Ada Lovelace", "ada@example.com", "A role for you",
				"I would like to talk to you about a backend role.", "token-from-the-widget", "   ");

		contactService.submit(spaced, CLIENT_IP);

		then(contactMessageRepository).should().save(any());
		then(emailService).should().send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void neverStoresTheTurnstileToken() {
		givenTheMessageIsStored();
		givenEmailSucceeds();

		contactService.submit(request(), CLIENT_IP);

		ContactMessage stored = capturedMessage();
		assertThat(stored.toString()).doesNotContain("token-from-the-widget");
	}

	@Test
	void doesNotEmailWhenNothingWasStored() {
		willThrow(new TurnstileFailedException("Turnstile verification failed."))
				.given(turnstileService).verify(anyString(), anyString());

		assertThatExceptionOfType(TurnstileFailedException.class)
				.isThrownBy(() -> contactService.submit(request(), CLIENT_IP));

		then(emailService).should(never()).send(anyString(), anyString(), anyString(), anyString());
	}

	/** Stubs the insert, returning the id the database would have assigned. */
	private UUID givenTheMessageIsStored() {
		UUID id = UUID.fromString("9f1c0d6e-0000-4000-8000-00000000abcd");
		given(contactMessageRepository.save(any(ContactMessage.class)))
				.willAnswer(invocation -> withId(invocation.getArgument(0), id));
		return id;
	}

	private void givenEmailSucceeds() {
		given(emailService.send(anyString(), anyString(), anyString(), anyString()))
				.willReturn(CompletableFuture.completedFuture(null));
	}

	private ContactMessage capturedMessage() {
		ArgumentCaptor<ContactMessage> saved = ArgumentCaptor.forClass(ContactMessage.class);
		then(contactMessageRepository).should().save(saved.capture());
		return saved.getValue();
	}

	private static ContactMessage withId(ContactMessage message, UUID id) {
		return new ContactMessage(id, message.name(), message.email(), message.subject(),
				message.message(), message.ipHash(), message.status(), message.createdAt());
	}

	private static ContactRequest request() {
		return request("A role for you");
	}

	private static ContactRequest request(String subject) {
		return new ContactRequest("Ada Lovelace", "ada@example.com", subject,
				"I would like to talk to you about a backend role.", "token-from-the-widget", null);
	}

}
