package dev.saq.portfolio.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import dev.saq.portfolio.dto.request.ContactRequest;
import dev.saq.portfolio.model.ContactMessage;
import dev.saq.portfolio.model.DeliveryStatus;

import org.junit.jupiter.api.Test;

/** What a submission turns into on its way to the table, and back out as a response. */
class ContactMapperTest {

	private static final String IP_HASH = "a".repeat(64);

	private final ContactMapper mapper = new ContactMapper();

	@Test
	void mapsASubmissionToAQueuedMessage() {
		ContactMessage message = mapper.toEntity(request("A role for you", null), IP_HASH);

		assertThat(message.id()).isNull();
		assertThat(message.createdAt()).isNull();
		assertThat(message.status()).isEqualTo(DeliveryStatus.QUEUED);
		assertThat(message.ipHash()).isEqualTo(IP_HASH);
		assertThat(message.name()).isEqualTo("Ada Lovelace");
		assertThat(message.email()).isEqualTo("ada@example.com");
		assertThat(message.subject()).isEqualTo("A role for you");
	}

	@Test
	void stripsSurroundingWhitespace() {
		ContactRequest padded = new ContactRequest("  Ada Lovelace  ", "  ada@example.com  ",
				"  A role for you  ", "  I would like to talk to you.  ", "token", null);

		ContactMessage message = mapper.toEntity(padded, IP_HASH);

		assertThat(message.name()).isEqualTo("Ada Lovelace");
		assertThat(message.email()).isEqualTo("ada@example.com");
		assertThat(message.subject()).isEqualTo("A role for you");
		assertThat(message.message()).isEqualTo("I would like to talk to you.");
	}

	@Test
	void treatsAnOmittedAndABlankSubjectAlike() {
		assertThat(mapper.toEntity(request(null, null), IP_HASH).subject()).isNull();
		assertThat(mapper.toEntity(request("   ", null), IP_HASH).subject()).isNull();
	}

	@Test
	void keepsNeitherTheTokenNorTheHoneypot() {
		ContactMessage message = mapper
				.toEntity(request("A role for you", "https://spam.example"), IP_HASH);

		assertThat(message.toString())
				.doesNotContain("token")
				.doesNotContain("spam.example");
	}

	@Test
	void mapsAStoredMessageToTheAcceptedResponse() {
		UUID id = UUID.randomUUID();
		ContactMessage stored = new ContactMessage(id, "Ada Lovelace", "ada@example.com", null,
				"I would like to talk to you.", IP_HASH, DeliveryStatus.QUEUED, null);

		assertThat(mapper.toResponse(stored).id()).isEqualTo(id);
		assertThat(mapper.toResponse(stored).status()).isEqualTo("queued");
	}

	private static ContactRequest request(String subject, String website) {
		return new ContactRequest("Ada Lovelace", "ada@example.com", subject,
				"I would like to talk to you.", "token", website);
	}

}
