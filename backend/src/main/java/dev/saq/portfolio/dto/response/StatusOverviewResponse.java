package dev.saq.portfolio.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/status} — the current state of everything being monitored.
 *
 * <p>Mirrors the {@code StatusOverview} schema in {@code docs/api/portfolio-api.yaml}. States are
 * the contract's lowercase spellings; {@link dev.saq.portfolio.mapper.StatusMapper} is where they
 * are converted.
 *
 * @param overall   the worst state among the services, or {@code unknown} when nothing is
 *                  monitored or nothing has been probed
 * @param services  one entry per configured service, in configuration order, possibly none
 * @param checkedAt the most recent probe across all services, or the time of the request when
 *                  there has not been one
 */
public record StatusOverviewResponse(
		String overall,
		List<ServiceStatus> services,
		Instant checkedAt) {

	/**
	 * One monitored project.
	 *
	 * @param responseTimeMs the latest measured round trip, or {@code null} when the latest probe
	 *                       did not complete or there has not been one
	 * @param uptime30d      percentage of probes in the last thirty days that the service
	 *                       answered, {@code 0} when it has never been probed
	 */
	public record ServiceStatus(
			String slug,
			String name,
			String url,
			String state,
			Integer responseTimeMs,
			double uptime30d) {
	}

}
