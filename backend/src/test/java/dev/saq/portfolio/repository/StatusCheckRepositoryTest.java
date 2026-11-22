package dev.saq.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.TestcontainersConfig;
import dev.saq.portfolio.dto.internal.StatusBucketRow;
import dev.saq.portfolio.dto.internal.StatusUptimeRow;
import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusCheck;
import dev.saq.portfolio.model.StatusWindow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The bucketing and uptime queries against the real schema from {@code V4__status.sql}, on the
 * same Postgres image local development uses.
 *
 * <p>Worth a real database rather than a stub for one reason above all: {@code date_bin} is
 * where the service's idea of a bucket and the database's have to coincide, and nothing but
 * Postgres can confirm they do. The {@code check} constraint and the {@code filter} aggregates
 * are in the same position.
 */
@DataJdbcTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class StatusCheckRepositoryTest {

	/** A bucket boundary for all three strides at once, which keeps the arithmetic readable. */
	private static final Instant MIDNIGHT = Instant.parse("2026-11-22T00:00:00Z");

	@Autowired
	private StatusCheckRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearChecks() {
		jdbcTemplate.update("delete from status_check");
	}

	@Test
	void assignsAnIdFromTheIdentityColumnOnInsert() {
		StatusCheck saved = repository.save(check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182));

		assertThat(saved.id()).isNotNull();
	}

	@Test
	void roundTripsEveryFieldIncludingTheStateEnum() {
		Long id = repository.save(check("githmind", MIDNIGHT, ServiceState.DEGRADED, 2400)).id();

		assertThat(repository.findById(id)).isPresent().get().satisfies(check -> {
			assertThat(check.serviceSlug()).isEqualTo("githmind");
			assertThat(check.checkedAt()).isEqualTo(MIDNIGHT);
			assertThat(check.state()).isEqualTo(ServiceState.DEGRADED);
			assertThat(check.responseTimeMs()).isEqualTo(2400);
		});
	}

	@Test
	void storesADownCheckWithNoResponseTime() {
		Long id = repository.save(check("githmind", MIDNIGHT, ServiceState.DOWN, null)).id();

		assertThat(repository.findById(id)).get()
				.satisfies(check -> assertThat(check.responseTimeMs()).isNull());
	}

	@Test
	void refusesAStateTheEnumDoesNotStore() {
		// UNKNOWN is invented by the service for empty buckets and must never reach a row;
		// StatusCheck.record() guards the same thing one layer earlier.
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
				() -> jdbcTemplate.update(
						"insert into status_check (service_slug, checked_at, state) "
								+ "values (?, ?, 'UNKNOWN')",
						"githmind", java.sql.Timestamp.from(MIDNIGHT)));
	}

	@Test
	void findsTheNewestCheckForEachService() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT.minus(Duration.ofMinutes(10)), ServiceState.DOWN, null),
				check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182),
				check("medi-scan", MIDNIGHT.minus(Duration.ofMinutes(5)), ServiceState.OPERATIONAL, 240),
				check("medi-scan", MIDNIGHT, ServiceState.DEGRADED, 2400)));

		List<StatusCheck> latest = repository.findLatestPerService(List.of("githmind", "medi-scan"));

		assertThat(latest).hasSize(2)
				.anySatisfy(check -> {
					assertThat(check.serviceSlug()).isEqualTo("githmind");
					assertThat(check.state()).isEqualTo(ServiceState.OPERATIONAL);
					assertThat(check.responseTimeMs()).isEqualTo(182);
				})
				.anySatisfy(check -> {
					assertThat(check.serviceSlug()).isEqualTo("medi-scan");
					assertThat(check.state()).isEqualTo(ServiceState.DEGRADED);
				});
	}

	@Test
	void returnsNothingForAServiceThatHasNeverBeenProbed() {
		repository.save(check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182));

		List<StatusCheck> latest =
				repository.findLatestPerService(List.of("githmind", "never-probed"));

		assertThat(latest).hasSize(1);
		assertThat(latest.getFirst().serviceSlug()).isEqualTo("githmind");
	}

	@Test
	void ignoresServicesOutsideTheRequestedSet() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182),
				check("retired-project", MIDNIGHT, ServiceState.DOWN, null)));

		assertThat(repository.findLatestPerService(List.of("githmind"))).hasSize(1);
	}

	@Test
	void countsUptimeWithADegradedCheckCountingAsUp() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182),
				check("githmind", MIDNIGHT.plus(Duration.ofMinutes(5)), ServiceState.DEGRADED, 2400),
				check("githmind", MIDNIGHT.plus(Duration.ofMinutes(10)), ServiceState.DOWN, null),
				check("githmind", MIDNIGHT.plus(Duration.ofMinutes(15)), ServiceState.OPERATIONAL, 190)));

		List<StatusUptimeRow> rows =
				repository.findUptimeSince(List.of("githmind"), MIDNIGHT);

		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.totalChecks()).isEqualTo(4);
			assertThat(row.upChecks()).isEqualTo(3);
			assertThat(row.uptimePercent()).isEqualTo(75.0);
		});
	}

	@Test
	void countsUptimeOnlyInsideTheWindow() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT.minus(Duration.ofDays(40)), ServiceState.DOWN, null),
				check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182)));

		List<StatusUptimeRow> rows = repository.findUptimeSince(List.of("githmind"), MIDNIGHT);

		assertThat(rows).singleElement()
				.satisfies(row -> assertThat(row.totalChecks()).isEqualTo(1));
	}

	@Test
	void producesNoUptimeRowForAServiceWithNoChecks() {
		// Which is what keeps "never probed" (unknown) distinguishable from "probed and down"
		// (0%) one layer up.
		assertThat(repository.findUptimeSince(List.of("never-probed"), MIDNIGHT)).isEmpty();
	}

	@Test
	void binsChecksIntoFiveMinuteBucketsTheServiceCanNameItself() {
		// Three checks inside one five-minute bucket and one in the next, so the grouping is
		// visible rather than incidental.
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT.plusSeconds(10), ServiceState.OPERATIONAL, 100),
				check("githmind", MIDNIGHT.plusSeconds(150), ServiceState.OPERATIONAL, 200),
				check("githmind", MIDNIGHT.plusSeconds(290), ServiceState.OPERATIONAL, 300),
				check("githmind", MIDNIGHT.plusSeconds(301), ServiceState.OPERATIONAL, 400)));

		List<StatusBucketRow> buckets = repository.findBuckets("githmind",
				StatusWindow.LAST_24H.pgInterval(), MIDNIGHT);

		assertThat(buckets).hasSize(2);
		assertThat(buckets.getFirst().bucketStart()).isEqualTo(MIDNIGHT);
		assertThat(buckets.getFirst().totalChecks()).isEqualTo(3);
		assertThat(buckets.getFirst().avgResponseTimeMs()).isEqualTo(200.0);
		assertThat(buckets.getLast().bucketStart())
				.isEqualTo(MIDNIGHT.plus(Duration.ofMinutes(5)));
		assertThat(buckets.getLast().totalChecks()).isEqualTo(1);
	}

	@Test
	void agreesWithTheWindowEnumAboutWhereEveryBucketBegins() {
		// The one thing a stubbed database could not tell us: that date_bin against the epoch
		// and StatusWindow.bucketStart() land on the same instant for every stride.
		Instant at = Instant.parse("2026-11-22T10:37:41Z");
		repository.save(check("githmind", at, ServiceState.OPERATIONAL, 182));

		for (StatusWindow window : StatusWindow.values()) {
			List<StatusBucketRow> buckets = repository.findBuckets("githmind",
					window.pgInterval(), window.windowStart(at));

			assertThat(buckets).as("stride %s", window.pgInterval()).singleElement()
					.satisfies(bucket -> assertThat(bucket.bucketStart())
							.isEqualTo(window.bucketStart(at)));
		}
	}

	@Test
	void reportsTheWorstStateInsideABucketRatherThanTheLatest() {
		// Alphabetically DEGRADED sorts before DOWN, so a max() over the text column would get
		// this backwards. The ordering is spelled out in SQL for exactly that reason.
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT.plusSeconds(10), ServiceState.DOWN, null),
				check("githmind", MIDNIGHT.plusSeconds(60), ServiceState.DEGRADED, 2400),
				check("githmind", MIDNIGHT.plusSeconds(120), ServiceState.OPERATIONAL, 182)));

		List<StatusBucketRow> buckets = repository.findBuckets("githmind",
				StatusWindow.LAST_24H.pgInterval(), MIDNIGHT);

		assertThat(buckets).singleElement()
				.satisfies(bucket -> assertThat(bucket.state()).isEqualTo(ServiceState.DOWN));
	}

	@Test
	void prefersDegradedOverOperationalWithinABucket() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT.plusSeconds(10), ServiceState.OPERATIONAL, 182),
				check("githmind", MIDNIGHT.plusSeconds(60), ServiceState.DEGRADED, 2400)));

		assertThat(repository.findBuckets("githmind", StatusWindow.LAST_24H.pgInterval(), MIDNIGHT))
				.singleElement()
				.satisfies(bucket -> assertThat(bucket.state()).isEqualTo(ServiceState.DEGRADED));
	}

	@Test
	void averagesOnlyTheProbesThatMeasuredSomething() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT.plusSeconds(10), ServiceState.OPERATIONAL, 100),
				check("githmind", MIDNIGHT.plusSeconds(60), ServiceState.DOWN, null),
				check("githmind", MIDNIGHT.plusSeconds(120), ServiceState.OPERATIONAL, 300)));

		assertThat(repository.findBuckets("githmind", StatusWindow.LAST_24H.pgInterval(), MIDNIGHT))
				.singleElement()
				.satisfies(bucket -> {
					assertThat(bucket.totalChecks()).isEqualTo(3);
					assertThat(bucket.upChecks()).isEqualTo(2);
					assertThat(bucket.avgResponseTimeMs()).isEqualTo(200.0);
					assertThat(bucket.roundedAvgResponseTimeMs()).isEqualTo(200);
				});
	}

	@Test
	void leavesTheAverageNullForABucketOfNothingButFailures() {
		repository.save(check("githmind", MIDNIGHT, ServiceState.DOWN, null));

		assertThat(repository.findBuckets("githmind", StatusWindow.LAST_24H.pgInterval(), MIDNIGHT))
				.singleElement()
				.satisfies(bucket -> {
					assertThat(bucket.avgResponseTimeMs()).isNull();
					assertThat(bucket.roundedAvgResponseTimeMs()).isNull();
				});
	}

	@Test
	void returnsBucketsForOneServiceOnly() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182),
				check("medi-scan", MIDNIGHT, ServiceState.DOWN, null)));

		assertThat(repository.findBuckets("githmind", StatusWindow.LAST_24H.pgInterval(), MIDNIGHT))
				.singleElement()
				.satisfies(bucket -> assertThat(bucket.state()).isEqualTo(ServiceState.OPERATIONAL));
	}

	@Test
	void skipsBucketsNobodyProbedRatherThanInventingThem() {
		// A gap comes back as a missing bucket, not as a zeroed one, which is what lets the
		// service fill it in as `unknown`.
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182),
				check("githmind", MIDNIGHT.plus(Duration.ofMinutes(15)), ServiceState.OPERATIONAL, 190)));

		List<StatusBucketRow> buckets = repository.findBuckets("githmind",
				StatusWindow.LAST_24H.pgInterval(), MIDNIGHT);

		assertThat(buckets).hasSize(2);
		assertThat(buckets).extracting(StatusBucketRow::bucketStart)
				.containsExactly(MIDNIGHT, MIDNIGHT.plus(Duration.ofMinutes(15)));
	}

	@Test
	void deletesOnlyChecksOlderThanTheCutoff() {
		repository.saveAll(List.of(
				check("githmind", MIDNIGHT.minus(Duration.ofDays(40)), ServiceState.OPERATIONAL, 182),
				check("githmind", MIDNIGHT.minus(Duration.ofDays(36)), ServiceState.DOWN, null),
				check("githmind", MIDNIGHT.minus(Duration.ofDays(1)), ServiceState.OPERATIONAL, 190)));

		int deleted = repository.deleteOlderThan(MIDNIGHT.minus(Duration.ofDays(35)));

		assertThat(deleted).isEqualTo(2);
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void deletesNothingWhenEverythingIsInsideTheWindow() {
		repository.save(check("githmind", MIDNIGHT, ServiceState.OPERATIONAL, 182));

		assertThat(repository.deleteOlderThan(MIDNIGHT.minus(Duration.ofDays(35)))).isZero();
	}

	private static StatusCheck check(String slug, Instant at, ServiceState state, Integer ms) {
		return StatusCheck.record(slug, at, state, ms);
	}

}
