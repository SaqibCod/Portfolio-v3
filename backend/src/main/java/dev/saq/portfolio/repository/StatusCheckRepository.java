package dev.saq.portfolio.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import dev.saq.portfolio.dto.internal.StatusBucketRow;
import dev.saq.portfolio.dto.internal.StatusUptimeRow;
import dev.saq.portfolio.model.StatusCheck;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link StatusCheck}: inserts from the probe job, three read shapes for the
 * status page, and the retention sweep.
 *
 * <p>Everything aggregates in SQL rather than in Java. A thirty-day window at a five-minute
 * interval is roughly 8,600 rows per service, and the page needs three figures out of them --
 * shipping all of it to the application to be counted would cost far more than the answer is
 * worth. Every query here is a range scan of {@code status_check_slug_time_idx}.
 *
 * <p>Both collection-parameter methods are unsafe to call with an empty collection:
 * {@code in ()} is not valid SQL. The caller is
 * {@link dev.saq.portfolio.service.StatusService}, which never reaches this class when nothing
 * is monitored -- and that is a supported configuration, not an edge case, so it is tested.
 *
 * <p>Buckets are produced by {@code date_bin} rather than {@code date_trunc}, including for the
 * hourly and daily strides. {@code date_trunc} on a {@code timestamptz} resolves against the
 * session time zone, which would make a "day" mean something different on a server configured
 * differently and would give a thirty-day window thirty-one partial buckets. Binned against
 * {@code timestamptz 'epoch'}, a bucket is the same stretch of time everywhere, and
 * {@link dev.saq.portfolio.model.StatusWindow#bucketStart} can name it without consulting a
 * calendar.
 */
public interface StatusCheckRepository extends ListCrudRepository<StatusCheck, Long> {

	/**
	 * The most recent check for each of the named services, which is each service's current state.
	 *
	 * <p>{@code distinct on} rather than a window function or a correlated subquery: it walks the
	 * index backwards per slug and stops at the first row, so the cost is one index seek per
	 * service regardless of how much history sits behind it. A service that has never been
	 * probed simply has no row here.
	 *
	 * @param slugs the configured service slugs; must not be empty
	 */
	@Query("""
			select distinct on (service_slug)
			       id, service_slug, checked_at, state, response_time_ms
			from status_check
			where service_slug in (:slugs)
			order by service_slug, checked_at desc""")
	List<StatusCheck> findLatestPerService(@Param("slugs") Collection<String> slugs);

	/**
	 * Probe counts per service since an instant, for the overview's uptime figure.
	 *
	 * <p>A degraded check counts as up: the service answered, just slowly, and a reader comparing
	 * this against a provider's SLA expects "did it serve the request" rather than "did it serve
	 * it quickly". The response-time column carries the speed story separately.
	 *
	 * @param slugs the configured service slugs; must not be empty
	 * @param from  the start of the window, inclusive
	 */
	@Query("""
			select service_slug,
			       count(*) as total_checks,
			       count(*) filter (where state <> 'DOWN') as up_checks
			from status_check
			where service_slug in (:slugs)
			  and checked_at >= :from
			group by service_slug""")
	List<StatusUptimeRow> findUptimeSince(@Param("slugs") Collection<String> slugs,
			@Param("from") Instant from);

	/**
	 * One service's history, aggregated into buckets of the given stride.
	 *
	 * <p>The worst state in a bucket is picked with nested {@code filter} counts rather than
	 * {@code max(state)}, which would order the strings alphabetically and rank {@code DEGRADED}
	 * above {@code DOWN}. The three stored states are ordered explicitly here;
	 * {@link dev.saq.portfolio.model.ServiceState#worst} does the same job in Java for the
	 * overall banner, and the two orderings agree.
	 *
	 * <p>{@code avg(response_time_ms)} ignores nulls, so the average covers exactly the probes
	 * that completed and a bucket of nothing but failures averages to null rather than to zero.
	 *
	 * @param slug   the service to read
	 * @param stride a PostgreSQL interval literal, from
	 *               {@link dev.saq.portfolio.model.StatusWindow#pgInterval()}
	 * @param from   the start of the oldest bucket, inclusive
	 */
	@Query("""
			select date_bin(cast(:stride as interval), checked_at, timestamptz 'epoch')
			         as bucket_start,
			       case
			         when count(*) filter (where state = 'DOWN') > 0 then 'DOWN'
			         when count(*) filter (where state = 'DEGRADED') > 0 then 'DEGRADED'
			         else 'OPERATIONAL'
			       end as state,
			       count(*) as total_checks,
			       count(*) filter (where state <> 'DOWN') as up_checks,
			       avg(response_time_ms) as avg_response_time_ms
			from status_check
			where service_slug = :slug
			  and checked_at >= :from
			group by bucket_start
			order by bucket_start""")
	List<StatusBucketRow> findBuckets(@Param("slug") String slug,
			@Param("stride") String stride, @Param("from") Instant from);

	/**
	 * Drops everything older than the retention window.
	 *
	 * @return how many checks were deleted, which is what the job logs
	 */
	@Modifying
	@Query("delete from status_check where checked_at < :cutoff")
	int deleteOlderThan(@Param("cutoff") Instant cutoff);

}
