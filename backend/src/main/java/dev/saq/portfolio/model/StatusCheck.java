package dev.saq.portfolio.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One health probe of one monitored project.
 *
 * <p>Rows are only ever inserted and, much later, deleted by the retention job: a probe records
 * what was true at a moment, and a moment does not change its mind. Nothing updates a check.
 *
 * <p>{@code checkedAt} is written from the application rather than taken from a column default,
 * so every check in one scheduled sweep shares a timestamp and lands in the same history bucket
 * however long the sweep takes.
 *
 * @param id             {@code null} before the insert; filled in from the identity column
 * @param serviceSlug    which configured service was probed
 * @param state          never {@link ServiceState#UNKNOWN} -- a stored check always measured
 *                       something
 * @param responseTimeMs the round trip in milliseconds, or {@code null} when the probe never
 *                       completed
 */
@Table("status_check")
public record StatusCheck(
		@Id Long id,
		String serviceSlug,
		Instant checkedAt,
		ServiceState state,
		Integer responseTimeMs) {

	/** A check as it looks before it is stored: no id, and a state the column will accept. */
	public static StatusCheck record(String serviceSlug, Instant checkedAt, ServiceState state,
			Integer responseTimeMs) {

		if (!state.isStored()) {
			throw new IllegalArgumentException("UNKNOWN is never stored as a check");
		}
		return new StatusCheck(null, serviceSlug, checkedAt, state, responseTimeMs);
	}

}
