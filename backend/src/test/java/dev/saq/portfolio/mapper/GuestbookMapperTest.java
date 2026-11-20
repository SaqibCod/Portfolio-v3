package dev.saq.portfolio.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.saq.portfolio.dto.request.CreateGuestbookEntryRequest;
import dev.saq.portfolio.dto.response.AdminGuestbookEntryResponse;
import dev.saq.portfolio.dto.response.GuestbookEntryResponse;
import dev.saq.portfolio.model.GuestbookEntry;
import dev.saq.portfolio.model.GuestbookStatus;

import org.junit.jupiter.api.Test;

/** What crosses each boundary, and — just as importantly — what does not. */
class GuestbookMapperTest {

	private static final String IP_HASH = "0123456789abcdef".repeat(4);

	private static final Instant CREATED_AT = Instant.parse("2026-11-20T10:15:00Z");

	private static final Instant MODERATED_AT = Instant.parse("2026-11-20T11:00:00Z");

	private final GuestbookMapper mapper = new GuestbookMapper();

	@Test
	void turnsASubmissionIntoAPendingEntry() {
		GuestbookEntry entity = mapper.toEntity(request("Priya", "Loved the status page."), IP_HASH);

		assertThat(entity.id()).isNull();
		assertThat(entity.name()).isEqualTo("Priya");
		assertThat(entity.message()).isEqualTo("Loved the status page.");
		assertThat(entity.status()).isEqualTo(GuestbookStatus.PENDING);
		assertThat(entity.ipHash()).isEqualTo(IP_HASH);
		assertThat(entity.createdAt()).isNull();
		assertThat(entity.moderatedAt()).isNull();
	}

	@Test
	void stripsSurroundingWhitespaceFromTheNameAndMessage() {
		GuestbookEntry entity = mapper.toEntity(request("  Priya  ", "\n  Hello there.  \n"), IP_HASH);

		assertThat(entity.name()).isEqualTo("Priya");
		assertThat(entity.message()).isEqualTo("Hello there.");
	}

	@Test
	void keepsWhitespaceInsideAMessage() {
		// Only the edges are noise; a visitor writing two paragraphs meant to.
		GuestbookEntry entity = mapper.toEntity(request("Priya", "Line one.\n\nLine two."), IP_HASH);

		assertThat(entity.message()).isEqualTo("Line one.\n\nLine two.");
	}

	@Test
	void neverCarriesTheTurnstileTokenOrHoneypotIntoTheEntity() {
		CreateGuestbookEntryRequest request = new CreateGuestbookEntryRequest("Priya", "Hello!",
				"token-from-the-widget", "https://spam.example");

		GuestbookEntry entity = mapper.toEntity(request, IP_HASH);

		assertThat(entity.toString()).doesNotContain("token-from-the-widget")
				.doesNotContain("spam.example");
	}

	@Test
	void mapsAnEntryToThePublicShape() {
		UUID id = UUID.randomUUID();

		GuestbookEntryResponse response = mapper.toResponse(approved(id));

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.name()).isEqualTo("Priya");
		assertThat(response.message()).isEqualTo("Loved the status page.");
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
	}

	@Test
	void keepsTheHashedIpOutOfThePublicShape() {
		// The record has no field for it, so this is a guard against someone adding one.
		assertThat(mapper.toResponse(approved(UUID.randomUUID())).toString())
				.doesNotContain(IP_HASH);
	}

	@Test
	void mapsAnEntryToTheAdminShapeWithTheContractsStatusSpelling() {
		UUID id = UUID.randomUUID();

		AdminGuestbookEntryResponse response = mapper.toAdminResponse(approved(id));

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.status()).isEqualTo("approved");
		assertThat(response.moderatedAt()).isEqualTo(MODERATED_AT);
	}

	@Test
	void reportsAPendingEntryAsPendingWithNoModerationTime() {
		GuestbookEntry entry = new GuestbookEntry(UUID.randomUUID(), "Priya", "Hello!",
				GuestbookStatus.PENDING, IP_HASH, CREATED_AT, null);

		AdminGuestbookEntryResponse response = mapper.toAdminResponse(entry);

		assertThat(response.status()).isEqualTo("pending");
		assertThat(response.moderatedAt()).isNull();
	}

	@Test
	void keepsTheHashedIpOutOfTheAdminShapeToo() {
		assertThat(mapper.toAdminResponse(approved(UUID.randomUUID())).toString())
				.doesNotContain(IP_HASH);
	}

	@Test
	void mapsAListInOrder() {
		List<GuestbookEntry> entries = List.of(approved(UUID.randomUUID()),
				approved(UUID.randomUUID()));

		assertThat(mapper.toResponses(entries)).extracting(GuestbookEntryResponse::id)
				.containsExactly(entries.get(0).id(), entries.get(1).id());
		assertThat(mapper.toAdminResponses(entries)).extracting(AdminGuestbookEntryResponse::id)
				.containsExactly(entries.get(0).id(), entries.get(1).id());
	}

	@Test
	void mapsAnEmptyListToAnEmptyList() {
		assertThat(mapper.toResponses(List.of())).isEmpty();
		assertThat(mapper.toAdminResponses(List.of())).isEmpty();
	}

	private static CreateGuestbookEntryRequest request(String name, String message) {
		return new CreateGuestbookEntryRequest(name, message, "token-from-the-widget", null);
	}

	private static GuestbookEntry approved(UUID id) {
		return new GuestbookEntry(id, "Priya", "Loved the status page.", GuestbookStatus.APPROVED,
				IP_HASH, CREATED_AT, MODERATED_AT);
	}

}
