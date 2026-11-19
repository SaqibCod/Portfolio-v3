package dev.saq.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.saq.portfolio.TestcontainersConfig;
import dev.saq.portfolio.model.ContactMessage;
import dev.saq.portfolio.model.DeliveryStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The entity against the real schema from {@code V2__contact.sql}, on the same Postgres image
 * local development uses.
 *
 * <p>Worth running against a real database rather than a stub: the id and the timestamp both come
 * from column defaults, the status is constrained by a {@code check}, and every width is pinned
 * to the API contract — none of which a mocked repository would notice.
 */
@DataJdbcTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ContactMessageRepositoryTest {

	@Autowired
	private ContactMessageRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void assignsAnIdFromTheDatabaseOnInsert() {
		ContactMessage saved = repository.save(queued("ada@example.com"));

		assertThat(saved.id()).isNotNull();
	}

	@Test
	void fillsInCreatedAtFromTheColumnDefault() {
		Instant before = Instant.now().minusSeconds(5);
		UUID id = repository.save(queued("ada@example.com")).id();

		Instant createdAt = jdbcTemplate.queryForObject(
				"SELECT created_at FROM contact_message WHERE id = ?", Instant.class, id);

		assertThat(createdAt).isNotNull().isAfter(before);
	}

	@Test
	void roundTripsEveryFieldIncludingTheStatusEnum() {
		UUID id = repository.save(queued("ada@example.com")).id();

		Optional<ContactMessage> found = repository.findById(id);

		assertThat(found).isPresent().get().satisfies(message -> {
			assertThat(message.id()).isEqualTo(id);
			assertThat(message.name()).isEqualTo("Ada Lovelace");
			assertThat(message.email()).isEqualTo("ada@example.com");
			assertThat(message.subject()).isEqualTo("A role for you");
			assertThat(message.message()).isEqualTo("I would like to talk to you.");
			assertThat(message.status()).isEqualTo(DeliveryStatus.QUEUED);
			assertThat(message.createdAt()).isNotNull();
		});
	}

	@Test
	void storesAMessageWithoutASubject() {
		ContactMessage saved = repository.save(new ContactMessage(null, "Ada Lovelace",
				"ada@example.com", null, "I would like to talk to you.", ipHash(), DeliveryStatus.QUEUED,
				null));

		assertThat(repository.findById(saved.id())).get()
				.extracting(ContactMessage::subject)
				.isNull();
	}

	@Test
	void advancesTheDeliveryStatus() {
		UUID id = repository.save(queued("ada@example.com")).id();

		assertThat(repository.updateStatus(id, DeliveryStatus.SENT)).isEqualTo(1);
		assertThat(repository.findById(id)).get()
				.extracting(ContactMessage::status)
				.isEqualTo(DeliveryStatus.SENT);
	}

	@Test
	void recordsAFailedDelivery() {
		UUID id = repository.save(queued("ada@example.com")).id();

		assertThat(repository.updateStatus(id, DeliveryStatus.FAILED)).isEqualTo(1);
		assertThat(repository.findById(id)).get()
				.extracting(ContactMessage::status)
				.isEqualTo(DeliveryStatus.FAILED);
	}

	@Test
	void changesNothingWhenTheMessageIsGone() {
		assertThat(repository.updateStatus(UUID.randomUUID(), DeliveryStatus.SENT)).isZero();
	}

	@Test
	void leavesTheRestOfTheRowUntouchedWhenTheStatusChanges() {
		UUID id = repository.save(queued("ada@example.com")).id();
		Instant createdAt = jdbcTemplate.queryForObject(
				"SELECT created_at FROM contact_message WHERE id = ?", Instant.class, id);

		repository.updateStatus(id, DeliveryStatus.SENT);

		assertThat(repository.findById(id)).get().satisfies(message -> {
			assertThat(message.email()).isEqualTo("ada@example.com");
			assertThat(message.message()).isEqualTo("I would like to talk to you.");
			assertThat(message.createdAt()).isEqualTo(createdAt);
		});
	}

	@Test
	void refusesAStatusTheCheckConstraintDoesNotAllow() {
		// Guards the enum against drifting from the constraint in V2__contact.sql.
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
				() -> jdbcTemplate.update("""
						INSERT INTO contact_message (name, email, message, ip_hash, status)
						VALUES ('Ada', 'ada@example.com', 'A message here.', ?, 'DELIVERED')""",
						ipHash()));
	}

	@Test
	void acceptsTheLongestValuesTheContractAllows() {
		ContactMessage saved = repository.save(new ContactMessage(null, "n".repeat(100),
				"e".repeat(242) + "@example.com", "s".repeat(150), "m".repeat(5000), ipHash(),
				DeliveryStatus.QUEUED, null));

		assertThat(repository.findById(saved.id())).get()
				.extracting(message -> message.message().length())
				.isEqualTo(5000);
	}

	private static ContactMessage queued(String email) {
		return ContactMessage.queued("Ada Lovelace", email, "A role for you",
				"I would like to talk to you.", ipHash());
	}

	/** Any 64-character hex string; the column is a fixed-width {@code char(64)}. */
	private static String ipHash() {
		return "0123456789abcdef".repeat(4);
	}

}
