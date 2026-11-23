package dev.saq.portfolio.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Bucket counts and epoch alignment. The counts are published in the contract, and the alignment
 * is the agreement between this enum and the {@code date_bin} call in the repository -- if the
 * two disagreed, checks would land in buckets the timeline does not have.
 */
class StatusWindowTest {

	@Test
	void carriesTheBucketCountsTheContractDocuments() {
		assertThat(StatusWindow.LAST_24H.buckets()).isEqualTo(288);
		assertThat(StatusWindow.LAST_7D.buckets()).isEqualTo(168);
		assertThat(StatusWindow.LAST_30D.buckets()).isEqualTo(30);
	}

	@Test
	void eachWindowSpansExactlyItsName() {
		assertThat(StatusWindow.LAST_24H.stride().multipliedBy(288))
				.isEqualTo(Duration.ofHours(24));
		assertThat(StatusWindow.LAST_7D.stride().multipliedBy(168))
				.isEqualTo(Duration.ofDays(7));
		assertThat(StatusWindow.LAST_30D.stride().multipliedBy(30))
				.isEqualTo(Duration.ofDays(30));
	}

	@Test
	void defaultsToTwentyFourHoursLikeTheContract() {
		assertThat(StatusWindow.DEFAULT).isEqualTo(StatusWindow.LAST_24H);
		assertThat(StatusWindow.DEFAULT.toApi()).isEqualTo("24h");
	}

	@Test
	void roundTripsEveryApiValue() {
		for (StatusWindow window : StatusWindow.values()) {
			assertThat(StatusWindow.fromApi(window.toApi())).isEqualTo(window);
		}
	}

	@Test
	void rejectsAWindowTheContractDoesNotOffer() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> StatusWindow.fromApi("90d"));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> StatusWindow.fromApi(null));
	}

	@Test
	void roundsAnInstantDownToItsBucketStart() {
		Instant at = Instant.parse("2026-11-22T10:37:41Z");

		assertThat(StatusWindow.LAST_24H.bucketStart(at))
				.isEqualTo(Instant.parse("2026-11-22T10:35:00Z"));
		assertThat(StatusWindow.LAST_7D.bucketStart(at))
				.isEqualTo(Instant.parse("2026-11-22T10:00:00Z"));
		assertThat(StatusWindow.LAST_30D.bucketStart(at))
				.isEqualTo(Instant.parse("2026-11-22T00:00:00Z"));
	}

	@Test
	void alignsBucketsToTheEpochRatherThanToTheRequest() {
		// Two requests seconds apart must agree on where every bucket begins, or a visitor
		// refreshing the page would watch the whole strip shift under them.
		Instant first = Instant.parse("2026-11-22T10:37:41Z");
		Instant second = Instant.parse("2026-11-22T10:38:02Z");

		assertThat(StatusWindow.LAST_24H.bucketStart(first))
				.isEqualTo(StatusWindow.LAST_24H.bucketStart(second));
	}

	@Test
	void theWindowReachesBackExactlyOneBucketShortOfItsSpan() {
		// The newest bucket is the partial one `now` sits in, so the oldest starts
		// (buckets - 1) strides earlier and the window covers the present moment.
		Instant now = Instant.parse("2026-11-22T10:37:41Z");

		assertThat(StatusWindow.LAST_24H.windowStart(now))
				.isEqualTo(Instant.parse("2026-11-21T10:40:00Z"));
		assertThat(StatusWindow.LAST_30D.windowStart(now))
				.isEqualTo(Instant.parse("2026-10-24T00:00:00Z"));
	}

	@Test
	void namesAPostgresIntervalForEveryStride() {
		assertThat(StatusWindow.LAST_24H.pgInterval()).isEqualTo("5 minutes");
		assertThat(StatusWindow.LAST_7D.pgInterval()).isEqualTo("1 hour");
		assertThat(StatusWindow.LAST_30D.pgInterval()).isEqualTo("1 day");
	}

}
