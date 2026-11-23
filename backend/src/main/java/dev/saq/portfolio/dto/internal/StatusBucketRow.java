package dev.saq.portfolio.dto.internal;

import java.time.Instant;

import dev.saq.portfolio.model.ServiceState;

/**
 * One non-empty history bucket, as the database aggregates it.
 *
 * <p>Only buckets that contain at least one check come back. The service lays these over the
 * timeline it built itself, which is what turns a gap in probing into an {@code unknown} point
 * rather than into a missing one -- a bucket that is absent here is a bucket nobody measured.
 *
 * @param bucketStart       the epoch-aligned start of the bucket, matching
 *                          {@link dev.saq.portfolio.model.StatusWindow#bucketStart}
 * @param state             the worst state recorded inside the bucket. A five-minute window that
 *                          contains one failure is not a healthy five minutes
 * @param totalChecks       every check in the bucket, so the window's uptime can be summed from
 *                          the same rows the chart is drawn from
 * @param upChecks          the checks that answered
 * @param avgResponseTimeMs the mean round trip of the checks that measured one, or {@code null}
 *                          when none did
 */
public record StatusBucketRow(
		Instant bucketStart,
		ServiceState state,
		long totalChecks,
		long upChecks,
		Double avgResponseTimeMs) {

	/** The average rounded to whole milliseconds, which is what the contract publishes. */
	public Integer roundedAvgResponseTimeMs() {
		return avgResponseTimeMs == null ? null : (int) Math.round(avgResponseTimeMs);
	}

}
