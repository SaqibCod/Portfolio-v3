package dev.saq.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.saq.portfolio.TestcontainersConfig;
import dev.saq.portfolio.model.GuestbookEntry;
import dev.saq.portfolio.model.GuestbookStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The entity and the keyset queries against the real schema from {@code V3__guestbook.sql}, on
 * the same Postgres image local development uses.
 *
 * <p>Worth running against a real database rather than a stub: the id, the timestamp and the
 * default status all come from column defaults, the status is constrained by a {@code check}, and
 * the page queries lean on Postgres row-value comparison and a parameterised {@code limit} — none
 * of which a mocked repository would notice.
 */
@DataJdbcTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class GuestbookRepositoryTest {

	@Autowired
	private GuestbookRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void assignsAnIdFromTheDatabaseOnInsert() {
		GuestbookEntry saved = repository.save(GuestbookEntry.pending("Priya", "Hello!", ipHash()));

		assertThat(saved.id()).isNotNull();
	}

	@Test
	void fillsInCreatedAtFromTheColumnDefault() {
		Instant before = Instant.now().minusSeconds(5);

		UUID id = repository.save(GuestbookEntry.pending("Priya", "Hello!", ipHash())).id();

		Instant createdAt = jdbcTemplate.queryForObject(
				"SELECT created_at FROM guestbook_entry WHERE id = ?", Instant.class, id);
		assertThat(createdAt).isNotNull().isAfter(before);
	}

	@Test
	void roundTripsEveryFieldAndStartsOutPending() {
		UUID id = repository.save(GuestbookEntry.pending("Priya", "Loved the status page.", ipHash()))
				.id();

		assertThat(repository.findById(id)).isPresent().get().satisfies(entry -> {
			assertThat(entry.id()).isEqualTo(id);
			assertThat(entry.name()).isEqualTo("Priya");
			assertThat(entry.message()).isEqualTo("Loved the status page.");
			assertThat(entry.status()).isEqualTo(GuestbookStatus.PENDING);
			assertThat(entry.ipHash()).isEqualTo(ipHash());
			assertThat(entry.createdAt()).isNotNull();
			assertThat(entry.moderatedAt()).isNull();
		});
	}

	@Test
	void refusesAStatusTheCheckConstraintDoesNotAllow() {
		// Guards the enum against drifting from the constraint in V3__guestbook.sql.
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
				() -> jdbcTemplate.update("""
						INSERT INTO guestbook_entry (name, message, ip_hash, status)
						VALUES ('Priya', 'Hello!', ?, 'MODERATED')""", ipHash()));
	}

	@Test
	void acceptsTheLongestValuesTheContractAllows() {
		GuestbookEntry saved = repository
				.save(GuestbookEntry.pending("n".repeat(50), "m".repeat(500), ipHash()));

		assertThat(repository.findById(saved.id())).get()
				.extracting(entry -> entry.message().length())
				.isEqualTo(500);
	}

	@Test
	void servesTheFirstPageNewestFirst() {
		seed(GuestbookStatus.APPROVED, 3);

		List<GuestbookEntry> page = repository.findFirstPage(GuestbookStatus.APPROVED, 10);

		assertThat(page).extracting(GuestbookEntry::message)
				.containsExactly("note 3", "note 2", "note 1");
	}

	@Test
	void servesOnlyTheRequestedStatus() {
		seed(GuestbookStatus.APPROVED, 2);
		seed(GuestbookStatus.PENDING, 3);
		seed(GuestbookStatus.REJECTED, 4);

		assertThat(repository.findFirstPage(GuestbookStatus.APPROVED, 10)).hasSize(2);
		assertThat(repository.findFirstPage(GuestbookStatus.PENDING, 10)).hasSize(3);
		assertThat(repository.findFirstPage(GuestbookStatus.REJECTED, 10)).hasSize(4);
	}

	@Test
	void readsNoMoreRowsThanTheLimit() {
		seed(GuestbookStatus.APPROVED, 5);

		assertThat(repository.findFirstPage(GuestbookStatus.APPROVED, 2)).hasSize(2);
	}

	@Test
	void continuesAfterTheCursorWithoutRepeatingIt() {
		seed(GuestbookStatus.APPROVED, 5);
		List<GuestbookEntry> first = repository.findFirstPage(GuestbookStatus.APPROVED, 2);
		GuestbookEntry cursor = first.getLast();

		List<GuestbookEntry> next = repository.findPageAfter(GuestbookStatus.APPROVED,
				cursor.createdAt(), cursor.id(), 2);

		assertThat(next).extracting(GuestbookEntry::message).containsExactly("note 3", "note 2");
		assertThat(next).extracting(GuestbookEntry::id).doesNotContain(cursor.id());
	}

	@Test
	void walksEveryEntryExactlyOnceAcrossPages() {
		seed(GuestbookStatus.APPROVED, 7);

		List<GuestbookEntry> walked = new ArrayList<>(
				repository.findFirstPage(GuestbookStatus.APPROVED, 3));
		while (true) {
			GuestbookEntry cursor = walked.getLast();
			List<GuestbookEntry> next = repository.findPageAfter(GuestbookStatus.APPROVED,
					cursor.createdAt(), cursor.id(), 3);
			if (next.isEmpty()) {
				break;
			}
			walked.addAll(next);
		}

		assertThat(walked).extracting(GuestbookEntry::message)
				.containsExactly("note 7", "note 6", "note 5", "note 4", "note 3", "note 2", "note 1");
		assertThat(walked).extracting(GuestbookEntry::id).doesNotHaveDuplicates();
	}

	@Test
	void breaksTimestampTiesByIdSoNoEntryStraddlesAPageBoundary() {
		// Three entries created in the same instant. Without the id in both the comparison and the
		// sort, one of these could be served twice or skipped entirely.
		Instant sameMoment = Instant.parse("2026-11-20T10:15:00Z");
		insertAt("tie a", GuestbookStatus.APPROVED, sameMoment);
		insertAt("tie b", GuestbookStatus.APPROVED, sameMoment);
		insertAt("tie c", GuestbookStatus.APPROVED, sameMoment);

		List<GuestbookEntry> first = repository.findFirstPage(GuestbookStatus.APPROVED, 2);
		GuestbookEntry cursor = first.getLast();
		List<GuestbookEntry> next = repository.findPageAfter(GuestbookStatus.APPROVED,
				cursor.createdAt(), cursor.id(), 2);

		// Split 2/1 across the boundary, with all three entries accounted for exactly once.
		// Deliberately no assertion on which id sorts where: Postgres orders uuid by unsigned
		// bytes and Java compares the halves as signed longs, so the two disagree on any id with
		// its high bit set and only the partition is meaningful here.
		assertThat(first).hasSize(2);
		assertThat(next).hasSize(1);

		List<String> walked = new ArrayList<>(first.stream().map(GuestbookEntry::message).toList());
		walked.add(next.getFirst().message());
		assertThat(walked).containsExactlyInAnyOrder("tie a", "tie b", "tie c");
	}

	@Test
	void returnsNothingWhenThereAreNoEntries() {
		assertThat(repository.findFirstPage(GuestbookStatus.APPROVED, 10)).isEmpty();
	}

	@Test
	void recordsAModerationDecision() {
		UUID id = repository.save(GuestbookEntry.pending("Priya", "Hello!", ipHash())).id();
		Instant decidedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

		assertThat(repository.updateStatus(id, GuestbookStatus.APPROVED, decidedAt)).isEqualTo(1);

		assertThat(repository.findById(id)).get().satisfies(entry -> {
			assertThat(entry.status()).isEqualTo(GuestbookStatus.APPROVED);
			assertThat(entry.moderatedAt()).isEqualTo(decidedAt);
		});
	}

	@Test
	void leavesTheNoteItselfUntouchedWhenModerated() {
		UUID id = repository.save(GuestbookEntry.pending("Priya", "Hello!", ipHash())).id();
		Instant createdAt = repository.findById(id).orElseThrow().createdAt();

		repository.updateStatus(id, GuestbookStatus.REJECTED, Instant.now());

		assertThat(repository.findById(id)).get().satisfies(entry -> {
			assertThat(entry.name()).isEqualTo("Priya");
			assertThat(entry.message()).isEqualTo("Hello!");
			assertThat(entry.createdAt()).isEqualTo(createdAt);
		});
	}

	@Test
	void changesNothingWhenModeratingAnEntryThatIsGone() {
		assertThat(repository.updateStatus(UUID.randomUUID(), GuestbookStatus.APPROVED, Instant.now()))
				.isZero();
	}

	@Test
	void deletesOneEntryAndReportsTheRowCount() {
		UUID id = repository.save(GuestbookEntry.pending("Priya", "Hello!", ipHash())).id();

		assertThat(repository.deleteEntry(id)).isEqualTo(1);
		assertThat(repository.findById(id)).isEmpty();
	}

	@Test
	void reportsNoRowsWhenDeletingAnEntryThatIsGone() {
		assertThat(repository.deleteEntry(UUID.randomUUID())).isZero();
	}

	/** {@code count} entries of one status, "note 1" oldest through "note n" newest. */
	private void seed(GuestbookStatus status, int count) {
		Instant base = Instant.parse("2026-11-01T00:00:00Z");
		for (int index = 1; index <= count; index++) {
			insertAt("note " + index, status, base.plusSeconds(index * 60L));
		}
	}

	/**
	 * Inserts with an explicit {@code created_at}, which {@code save} cannot do — the property is
	 * {@code @ReadOnlyProperty} precisely so that application code can never backdate an entry.
	 */
	private void insertAt(String message, GuestbookStatus status, Instant createdAt) {
		// OffsetDateTime rather than Instant: the PostgreSQL driver cannot infer a SQL type for a
		// bare Instant on a plain JdbcTemplate call. Spring Data JDBC converts it for us, which is
		// why the repository's own queries take Instant directly.
		jdbcTemplate.update("""
				INSERT INTO guestbook_entry (name, message, status, ip_hash, created_at)
				VALUES ('Priya', ?, ?, ?, ?)""",
				message, status.name(), ipHash(), createdAt.atOffset(ZoneOffset.UTC));
	}

	/** Any 64-character hex string; the column is a fixed-width {@code char(64)}. */
	private static String ipHash() {
		return "0123456789abcdef".repeat(4);
	}

}
