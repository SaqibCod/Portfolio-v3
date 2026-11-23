package dev.saq.portfolio.mapper;

import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.dto.response.StatusHistoryResponse;
import dev.saq.portfolio.dto.response.StatusOverviewResponse;
import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusWindow;

import org.springframework.stereotype.Component;

/**
 * Turns configuration, enums and query rows into the two status responses.
 *
 * <p>Two jobs, both small and both worth keeping out of the service: spelling states the way the
 * contract does, and rounding the two figures it publishes. Uptime is rounded here rather than in
 * SQL or in the client because the number of decimal places is a presentation decision -- the
 * contract's example is {@code 99.82}, so two is what callers get.
 */
@Component
public class StatusMapper {

	/** Matches the contract's {@code uptime30d} example, 99.82. */
	private static final int UPTIME_DECIMALS = 2;

	/**
	 * One row of the overview.
	 *
	 * @param service        the configured service, which is where the name and URL come from
	 * @param state          its current state
	 * @param responseTimeMs the latest measured round trip, or {@code null}
	 * @param uptime30d      raw percentage; rounded here
	 */
	public StatusOverviewResponse.ServiceStatus toServiceStatus(StatusProperties.Service service,
			ServiceState state, Integer responseTimeMs, double uptime30d) {

		return new StatusOverviewResponse.ServiceStatus(service.slug(), service.name(),
				service.url(), state.toApi(), responseTimeMs, roundPercent(uptime30d));
	}

	public StatusOverviewResponse toOverview(ServiceState overall,
			List<StatusOverviewResponse.ServiceStatus> services, Instant checkedAt) {

		return new StatusOverviewResponse(overall.toApi(), services, checkedAt);
	}

	public StatusHistoryResponse.Point toPoint(Instant bucketStart, ServiceState state,
			Integer avgResponseTimeMs) {

		return new StatusHistoryResponse.Point(bucketStart, state.toApi(), avgResponseTimeMs);
	}

	public StatusHistoryResponse toHistory(String slug, StatusWindow window, double uptime,
			List<StatusHistoryResponse.Point> points) {

		return new StatusHistoryResponse(slug, window.toApi(), roundPercent(uptime), points);
	}

	/**
	 * Rounds a percentage to two decimal places.
	 *
	 * <p>{@code BigDecimal} rather than the {@code Math.round(x * 100) / 100.0} shorthand, so the
	 * result is the nearest two-decimal value rather than whatever the binary double nearest to
	 * it happens to serialise as.
	 */
	private static double roundPercent(double percent) {
		return java.math.BigDecimal.valueOf(percent)
				.setScale(UPTIME_DECIMALS, java.math.RoundingMode.HALF_UP)
				.doubleValue();
	}

}
