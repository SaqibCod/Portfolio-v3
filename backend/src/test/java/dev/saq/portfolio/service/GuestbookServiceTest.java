package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import dev.saq.portfolio.dto.request.CreateGuestbookEntryRequest;
import dev.saq.portfolio.dto.response.AdminGuestbookEntryResponse;
import dev.saq.portfolio.dto.response.AdminGuestbookPageResponse;
import dev.saq.portfolio.dto.response.GuestbookEntryResponse;
import dev.saq.portfolio.dto.response.GuestbookPageResponse;
import dev.saq.portfolio.dto.response.GuestbookSubmissionResponse;
import dev.saq.portfolio.exception.InvalidCursorException;
import dev.saq.portfolio.exception.NotFoundException;
import dev.saq.portfolio.exception.TurnstileFailedException;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.mapper.GuestbookMapper;
import dev.saq.portfolio.model.GuestbookEntry;
import dev.saq.portfolio.model.GuestbookStatus;
import dev.saq.portfolio.repository.GuestbookRepository;
import dev.saq.portfolio.util.ClientIpUtils;
import dev.saq.portfolio.util.CursorCodec;
import dev.saq.testsupport.TestAppProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The screening order on submission, the keyset page assembly, and the moderation rules.
 *
 * <p>The mapper, the IP hasher and the cursor codec are the real implementations: all three are
 * pure, and using them means the assertions are about real values and real cursors rather than
 * about stubs agreeing with each other.
 */
@ExtendWith(MockitoExtension.class)
class GuestbookServiceTest {

	private static final String CLIENT_IP = "198.51.100.17";

	private static final Instant CREATED_AT = Instant.parse("2026-11-20T10:15:00Z");

	@Mock
	private GuestbookRepository guestbookRepository;

	@Mock
	private TurnstileService turnstileService;

	@Mock
	private EmailService emailService;

	private ClientIpUtils clientIpUtils;

	private CursorCodec cursorCodec;

	private GuestbookService guestbookService;

	@BeforeEach
	void setUp() {
		clientIpUtils = new ClientIpUtils(TestAppProperties.sample());
		cursorCodec = new CursorCodec();
		guestbookService = new GuestbookService(guestbookRepository, turnstileService, emailService,
				new GuestbookMapper(), clientIpUtils, cursorCodec, TestAppProperties.sample());
	}

	// --- create -------------------------------------------------------------------------------

	@Test
	void storesASubmissionAsPendingAndReportsItPending() {
		UUID id = UUID.randomUUID();
		givenTheEntryIsStoredAs(id, GuestbookStatus.PENDING);
		givenEmailSucceeds();

		GuestbookSubmissionResponse response = guestbookService.create(request(), CLIENT_IP);

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.status()).isEqualTo("pending");

		ArgumentCaptor<GuestbookEntry> saved = ArgumentCaptor.forClass(GuestbookEntry.class);
		then(guestbookRepository).should().save(saved.capture());
		assertThat(saved.getValue().status()).isEqualTo(GuestbookStatus.PENDING);
		assertThat(saved.getValue().name()).isEqualTo("Priya");
		assertThat(saved.getValue().message()).isEqualTo("Loved the status page.");
	}

	@Test
	void verifiesTheBotCheckBeforeStoringAnything() {
		willThrow(new TurnstileFailedException("Turnstile verification failed."))
				.given(turnstileService).verify(anyString(), any());

		assertThatExceptionOfType(TurnstileFailedException.class)
				.isThrownBy(() -> guestbookService.create(request(), CLIENT_IP));

		// A rejected submission never reaches the table, so it cannot be moderated into existence.
		verifyNoInteractions(guestbookRepository);
		verifyNoInteractions(emailService);
	}

	@Test
	void letsAnUnreachableCloudflareFailClosed() {
		willThrow(new UpstreamUnavailableException("Turnstile verification is unavailable.", 30, null))
				.given(turnstileService).verify(anyString(), any());

		assertThatExceptionOfType(UpstreamUnavailableException.class)
				.isThrownBy(() -> guestbookService.create(request(), CLIENT_IP));

		verifyNoInteractions(guestbookRepository);
	}

	@Test
	void answersAHoneypotSubmissionAsPendingButStoresNothing() {
		CreateGuestbookEntryRequest trapped = new CreateGuestbookEntryRequest("Spam Bot",
				"Buy our excellent product.", "token-from-the-widget", "https://spam.example");

		GuestbookSubmissionResponse response = guestbookService.create(trapped, CLIENT_IP);

		// Indistinguishable from a real 202, down to the shape of the id.
		assertThat(response.id()).isNotNull();
		assertThat(response.status()).isEqualTo("pending");
		verifyNoInteractions(guestbookRepository);
		verifyNoInteractions(emailService);
		// Not even verified: a trapped submission costs nothing upstream.
		verifyNoInteractions(turnstileService);
	}

	@Test
	void storesOnlyAHashedCallerAddress() {
		givenTheEntryIsStoredAs(UUID.randomUUID(), GuestbookStatus.PENDING);
		givenEmailSucceeds();

		guestbookService.create(request(), CLIENT_IP);

		ArgumentCaptor<GuestbookEntry> saved = ArgumentCaptor.forClass(GuestbookEntry.class);
		then(guestbookRepository).should().save(saved.capture());
		assertThat(saved.getValue().ipHash()).isEqualTo(clientIpUtils.hash(CLIENT_IP))
				.hasSize(64)
				.doesNotContain("198.51.100");
	}

	@Test
	void passesTheCallerAddressToCloudflareForRiskScoring() {
		givenTheEntryIsStoredAs(UUID.randomUUID(), GuestbookStatus.PENDING);
		givenEmailSucceeds();

		guestbookService.create(request(), CLIENT_IP);

		then(turnstileService).should().verify("token-from-the-widget", CLIENT_IP);
	}

	@Test
	void stillStoresAnEntryWhenThereIsNoCallerAddress() {
		givenTheEntryIsStoredAs(UUID.randomUUID(), GuestbookStatus.PENDING);
		givenEmailSucceeds();

		guestbookService.create(request(), null);

		ArgumentCaptor<GuestbookEntry> saved = ArgumentCaptor.forClass(GuestbookEntry.class);
		then(guestbookRepository).should().save(saved.capture());
		assertThat(saved.getValue().ipHash()).hasSize(64);
	}

	@Test
	void emailsTheOwnerAboutTheWaitingEntry() {
		UUID id = UUID.randomUUID();
		givenTheEntryIsStoredAs(id, GuestbookStatus.PENDING);
		givenEmailSucceeds();

		guestbookService.create(request(), CLIENT_IP);

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		then(emailService).should().send(eq(TestAppProperties.OWNER_EMAIL), subject.capture(),
				body.capture(), eq(null));

		assertThat(subject.getValue()).isEqualTo("[Portfolio] New guestbook entry from Priya");
		assertThat(body.getValue()).contains("Priya")
				.contains("Loved the status page.")
				.contains(id.toString());
	}

	@Test
	void keepsTheNotificationSubjectOnOneLine() {
		givenTheEntryIsStoredAs(UUID.randomUUID(), GuestbookStatus.PENDING,
				"Priya\r\nBcc: someone@example.com");
		givenEmailSucceeds();

		guestbookService.create(request(), CLIENT_IP);

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		then(emailService).should().send(anyString(), subject.capture(), anyString(), any());
		assertThat(subject.getValue()).doesNotContain("\n").doesNotContain("\r");
	}

	@Test
	void stillAcceptsTheEntryWhenTheNotificationFails() {
		UUID id = UUID.randomUUID();
		givenTheEntryIsStoredAs(id, GuestbookStatus.PENDING);
		given(emailService.send(anyString(), anyString(), anyString(), any()))
				.willReturn(CompletableFuture.failedFuture(new IllegalStateException("provider down")));

		// The note is already in the table; a lost notification must not lose the visitor's work.
		assertThat(guestbookService.create(request(), CLIENT_IP).id()).isEqualTo(id);
	}

	@Test
	void neverSendsTheOwnerTheVisitorAsAReplyTo() {
		givenTheEntryIsStoredAs(UUID.randomUUID(), GuestbookStatus.PENDING);
		givenEmailSucceeds();

		guestbookService.create(request(), CLIENT_IP);

		// The guestbook is anonymous: there is no address to reply to, by design.
		then(emailService).should().send(anyString(), anyString(), anyString(), eq(null));
	}

	// --- listApproved -------------------------------------------------------------------------

	@Test
	void servesOnlyApprovedEntriesToThePublic() {
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 3))
				.willReturn(entries(2));

		guestbookService.listApproved(null, 2);

		then(guestbookRepository).should().findFirstPage(GuestbookStatus.APPROVED, 3);
		then(guestbookRepository).should(never()).findFirstPage(eq(GuestbookStatus.PENDING), anyInt());
	}

	@Test
	void readsOneRowMoreThanThePageNeeds() {
		// The extra row is what decides whether there is a next page, without a count query.
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 21))
				.willReturn(entries(5));

		guestbookService.listApproved(null, 20);

		then(guestbookRepository).should().findFirstPage(GuestbookStatus.APPROVED, 21);
	}

	@Test
	void reportsNoNextCursorWhenThePageIsTheLastOne() {
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 3))
				.willReturn(entries(2));

		GuestbookPageResponse page = guestbookService.listApproved(null, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void reportsNoNextCursorWhenThePageIsExactlyFull() {
		// Exactly `limit` rows came back, so there is nothing beyond them.
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 3))
				.willReturn(entries(3).subList(0, 2));

		assertThat(guestbookService.listApproved(null, 2).nextCursor()).isNull();
	}

	@Test
	void dropsTheLookAheadRowFromThePage() {
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 3))
				.willReturn(entries(3));

		GuestbookPageResponse page = guestbookService.listApproved(null, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(page.items()).extracting(GuestbookEntryResponse::message)
				.containsExactly("note 0", "note 1");
	}

	@Test
	void pointsTheNextCursorAtTheLastEntryItReturned() {
		List<GuestbookEntry> rows = entries(3);
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 3)).willReturn(rows);

		GuestbookPageResponse page = guestbookService.listApproved(null, 2);

		// Not at the look-ahead row: the next request must seek past what the client has, no more.
		GuestbookEntry lastReturned = rows.get(1);
		assertThat(page.nextCursor())
				.isEqualTo(cursorCodec.encode(lastReturned.createdAt(), lastReturned.id()));
	}

	@Test
	void continuesFromACursorInsteadOfStartingOver() {
		GuestbookEntry position = entries(1).getFirst();
		String cursor = cursorCodec.encode(position.createdAt(), position.id());
		given(guestbookRepository.findPageAfter(GuestbookStatus.APPROVED, position.createdAt(),
				position.id(), 3)).willReturn(entries(2));

		guestbookService.listApproved(cursor, 2);

		then(guestbookRepository).should().findPageAfter(GuestbookStatus.APPROVED,
				position.createdAt(), position.id(), 3);
		then(guestbookRepository).should(never()).findFirstPage(any(), anyInt());
	}

	@Test
	void treatsABlankCursorAsNoCursor() {
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 3))
				.willReturn(entries(1));

		guestbookService.listApproved("   ", 2);

		then(guestbookRepository).should().findFirstPage(GuestbookStatus.APPROVED, 3);
	}

	@Test
	void refusesACursorItDidNotIssue() {
		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> guestbookService.listApproved("not-a-cursor!!", 2));

		verifyNoInteractions(guestbookRepository);
	}

	@Test
	void servesAnEmptyPageRatherThanFailingWhenThereAreNoEntries() {
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 21))
				.willReturn(List.of());

		GuestbookPageResponse page = guestbookService.listApproved(null, 20);

		assertThat(page.items()).isEmpty();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void keepsAPageWithinTheContractsCeiling() {
		given(guestbookRepository.findFirstPage(GuestbookStatus.APPROVED, 51))
				.willReturn(entries(10));

		guestbookService.listApproved(null, 5000);

		then(guestbookRepository).should().findFirstPage(GuestbookStatus.APPROVED, 51);
	}

	// --- listByStatus -------------------------------------------------------------------------

	@Test
	void servesTheOwnerTheStatusTheyAskedFor() {
		given(guestbookRepository.findFirstPage(GuestbookStatus.PENDING, 3))
				.willReturn(entries(2, GuestbookStatus.PENDING));

		AdminGuestbookPageResponse page = guestbookService.listByStatus(GuestbookStatus.PENDING,
				null, 2);

		assertThat(page.items()).extracting(AdminGuestbookEntryResponse::status)
				.containsOnly("pending");
	}

	@Test
	void pagesTheAdminListTheSameWayAsThePublicOne() {
		List<GuestbookEntry> rows = entries(3, GuestbookStatus.REJECTED);
		given(guestbookRepository.findFirstPage(GuestbookStatus.REJECTED, 3)).willReturn(rows);

		AdminGuestbookPageResponse page = guestbookService.listByStatus(GuestbookStatus.REJECTED,
				null, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(page.nextCursor())
				.isEqualTo(cursorCodec.encode(rows.get(1).createdAt(), rows.get(1).id()));
	}

	// --- moderate -----------------------------------------------------------------------------

	@Test
	void recordsADecisionAndReturnsTheUpdatedEntry() {
		UUID id = UUID.randomUUID();
		given(guestbookRepository.updateStatus(eq(id), eq(GuestbookStatus.APPROVED), any()))
				.willReturn(1);
		given(guestbookRepository.findById(id)).willReturn(Optional.of(
				new GuestbookEntry(id, "Priya", "Hello!", GuestbookStatus.APPROVED, ipHash(),
						CREATED_AT, CREATED_AT.plusSeconds(60))));

		AdminGuestbookEntryResponse response = guestbookService.moderate(id,
				GuestbookStatus.APPROVED);

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.status()).isEqualTo("approved");
		assertThat(response.moderatedAt()).isNotNull();
	}

	@Test
	void stampsTheDecisionWithTheTimeItWasMade() {
		UUID id = UUID.randomUUID();
		Instant before = Instant.now().minusSeconds(5);
		given(guestbookRepository.updateStatus(eq(id), any(), any())).willReturn(1);
		given(guestbookRepository.findById(id)).willReturn(Optional.of(
				new GuestbookEntry(id, "Priya", "Hello!", GuestbookStatus.REJECTED, ipHash(),
						CREATED_AT, CREATED_AT)));

		guestbookService.moderate(id, GuestbookStatus.REJECTED);

		ArgumentCaptor<Instant> moderatedAt = ArgumentCaptor.forClass(Instant.class);
		then(guestbookRepository).should().updateStatus(eq(id), eq(GuestbookStatus.REJECTED),
				moderatedAt.capture());
		assertThat(moderatedAt.getValue()).isAfter(before);
	}

	@Test
	void reportsAMissingEntryAsNotFoundRatherThanSilentlySucceeding() {
		UUID id = UUID.randomUUID();
		given(guestbookRepository.updateStatus(eq(id), any(), any())).willReturn(0);

		assertThatExceptionOfType(NotFoundException.class)
				.isThrownBy(() -> guestbookService.moderate(id, GuestbookStatus.APPROVED));

		then(guestbookRepository).should(never()).findById(any());
	}

	@Test
	void reportsNotFoundWhenTheEntryDisappearsBetweenTheUpdateAndTheRead() {
		UUID id = UUID.randomUUID();
		given(guestbookRepository.updateStatus(eq(id), any(), any())).willReturn(1);
		given(guestbookRepository.findById(id)).willReturn(Optional.empty());

		assertThatExceptionOfType(NotFoundException.class)
				.isThrownBy(() -> guestbookService.moderate(id, GuestbookStatus.APPROVED));
	}

	// --- delete -------------------------------------------------------------------------------

	@Test
	void deletesAnEntry() {
		UUID id = UUID.randomUUID();
		given(guestbookRepository.deleteEntry(id)).willReturn(1);

		guestbookService.delete(id);

		then(guestbookRepository).should().deleteEntry(id);
	}

	@Test
	void reportsDeletingAMissingEntryAsNotFound() {
		UUID id = UUID.randomUUID();
		given(guestbookRepository.deleteEntry(id)).willReturn(0);

		assertThatExceptionOfType(NotFoundException.class)
				.isThrownBy(() -> guestbookService.delete(id));
	}

	// --- fixtures -----------------------------------------------------------------------------

	private static CreateGuestbookEntryRequest request() {
		return new CreateGuestbookEntryRequest("Priya", "Loved the status page.",
				"token-from-the-widget", null);
	}

	private void givenTheEntryIsStoredAs(UUID id, GuestbookStatus status) {
		givenTheEntryIsStoredAs(id, status, "Priya");
	}

	/** {@code save} returns the row as the database wrote it: with an id and a timestamp. */
	private void givenTheEntryIsStoredAs(UUID id, GuestbookStatus status, String name) {
		given(guestbookRepository.save(any())).willReturn(new GuestbookEntry(id, name,
				"Loved the status page.", status, ipHash(), CREATED_AT, null));
	}

	private void givenEmailSucceeds() {
		given(emailService.send(anyString(), anyString(), anyString(), any()))
				.willReturn(CompletableFuture.completedFuture(null));
	}

	private static List<GuestbookEntry> entries(int count) {
		return entries(count, GuestbookStatus.APPROVED);
	}

	/** {@code count} entries, newest first, one minute apart — the order a page arrives in. */
	private static List<GuestbookEntry> entries(int count, GuestbookStatus status) {
		return IntStream.range(0, count)
				.mapToObj(index -> new GuestbookEntry(UUID.randomUUID(), "Priya", "note " + index,
						status, ipHash(), CREATED_AT.minusSeconds(index * 60L),
						(status == GuestbookStatus.PENDING) ? null : CREATED_AT))
				.toList();
	}

	private static String ipHash() {
		return "0123456789abcdef".repeat(4);
	}

}
