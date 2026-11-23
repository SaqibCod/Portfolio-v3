package dev.saq.portfolio.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.dto.internal.StatusBucketRow;
import dev.saq.portfolio.dto.internal.StatusUptimeRow;
import dev.saq.portfolio.dto.response.StatusHistoryResponse;
import dev.saq.portfolio.dto.response.StatusOverviewResponse;
import dev.saq.portfolio.exception.NotFoundException;
import dev.saq.portfolio.mapper.StatusMapper;
import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusCheck;
import dev.saq.portfolio.model.StatusWindow;
import dev.saq.portfolio.repository.StatusCheckRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the status page reads: the current state of every monitored project, and one project's
 * history over a window.
 *
 * <p>Configuration is the authority on which services exist; the table only knows about checks
 * that happened. So a service appears here because it is configured, not because it has been
 * probed — which is what gives a freshly added project an honest {@code unknown} instead of
 * leaving it off the page until the first sweep lands.
 *
 * <p>Monitoring nothing is a first-class state, not an edge case. Until a project is deployed
 * the service list is empty, and then this class answers out of configuration alone and never
 * reaches the repository. That is load-bearing rather than merely tidy: both multi-service
 * queries bind a collection into {@code in (:slugs)}, and {@code in ()} is a syntax error — an
 * unguarded empty list would turn the quietest possible configuration into a 500.
 *
 * <p>Both reads build their own timeline and let the database fill it in, rather than publishing
 * whatever rows happen to exist. A window therefore always carries its full bucket count and a
 * stretch nobody probed is a point marked {@code unknown}, which is a different statement from
 * leaving it out.
 */
@Service
public class StatusService {

	/** The window {@code uptime30d} is measured over, and the same one {@code 30d} history uses. */
	private static final StatusWindow UPTIME_WINDOW = StatusWindow.LAST_30D;

	private final StatusProperties statusProperties;

	private final StatusCheckRepository statusCheckRepository;

	private final StatusMapper statusMapper;

	private final Clock clock;

	/** Marked explicitly: there are two constructors, so Spring cannot deduce which to use. */
	@Autowired
	public StatusService(StatusProperties statusProperties,
			StatusCheckRepository statusCheckRepository, StatusMapper statusMapper) {

		this(statusProperties, statusCheckRepository, statusMapper, Clock.systemUTC());
	}

	/** Package-private so tests can pin the window boundaries. */
	StatusService(StatusProperties statusProperties, StatusCheckRepository statusCheckRepository,
			StatusMapper statusMapper, Clock clock) {

		this.statusProperties = statusProperties;
		this.statusCheckRepository = statusCheckRepository;
		this.statusMapper = statusMapper;
		this.clock = clock;
	}

	/**
	 * The current state of everything monitored.
	 *
	 * <p>{@code overall} is the worst of the per-service states, which for an empty list is
	 * {@code unknown} — see {@link ServiceState#worst}.
	 */
	@Transactional(readOnly = true)
	public StatusOverviewResponse getOverview() {
		Instant now = clock.instant();
		if (statusProperties.hasNoServices()) {
			return statusMapper.toOverview(ServiceState.UNKNOWN, List.of(), now);
		}

		List<String> slugs = statusProperties.services().stream()
				.map(StatusProperties.Service::slug)
				.toList();

		Map<String, StatusCheck> latest = statusCheckRepository.findLatestPerService(slugs).stream()
				.collect(Collectors.toMap(StatusCheck::serviceSlug, Function.identity(),
						// distinct on (service_slug) cannot produce a duplicate, but a map
						// collector throws rather than choosing if one ever did.
						(first, second) -> first, HashMap::new));

		Map<String, StatusUptimeRow> uptime = statusCheckRepository
				.findUptimeSince(slugs, UPTIME_WINDOW.windowStart(now)).stream()
				.collect(Collectors.toMap(StatusUptimeRow::serviceSlug, Function.identity()));

		List<StatusOverviewResponse.ServiceStatus> services = new ArrayList<>();
		List<ServiceState> states = new ArrayList<>();

		for (StatusProperties.Service service : statusProperties.services()) {
			StatusCheck check = latest.get(service.slug());
			ServiceState state = check == null ? ServiceState.UNKNOWN : check.state();
			Integer responseTimeMs = check == null ? null : check.responseTimeMs();
			StatusUptimeRow row = uptime.get(service.slug());

			states.add(state);
			services.add(statusMapper.toServiceStatus(service, state, responseTimeMs,
					row == null ? 0 : row.uptimePercent()));
		}

		return statusMapper.toOverview(ServiceState.worst(states), services,
				mostRecentCheck(latest.values(), now));
	}

	/**
	 * One project's history over a window.
	 *
	 * @throws NotFoundException if nothing by that slug is monitored. Checked against
	 *                           configuration rather than against the table, so a configured
	 *                           service that has never been probed answers with a window of
	 *                           {@code unknown} points instead of a 404
	 */
	@Transactional(readOnly = true)
	public StatusHistoryResponse getHistory(String slug, StatusWindow window) {
		StatusProperties.Service service = statusProperties.findBySlug(slug);
		if (service == null) {
			throw new NotFoundException("No monitored service with slug " + slug);
		}

		Instant now = clock.instant();
		Instant from = window.windowStart(now);
		Map<Instant, StatusBucketRow> buckets = statusCheckRepository
				.findBuckets(slug, window.pgInterval(), from).stream()
				.collect(Collectors.toMap(StatusBucketRow::bucketStart, Function.identity()));

		List<StatusHistoryResponse.Point> points = new ArrayList<>(window.buckets());
		for (int index = 0; index < window.buckets(); index++) {
			Instant bucketStart = from.plus(window.stride().multipliedBy(index));
			StatusBucketRow row = buckets.get(bucketStart);

			points.add(row == null
					? statusMapper.toPoint(bucketStart, ServiceState.UNKNOWN, null)
					: statusMapper.toPoint(bucketStart, row.state(),
							row.roundedAvgResponseTimeMs()));
		}

		return statusMapper.toHistory(slug, window, uptimeAcross(buckets.values()), points);
	}

	/**
	 * Uptime over the window, summed from the same buckets the chart is drawn from rather than
	 * queried again — one fewer round trip, and no chance of the figure and the chart disagreeing
	 * because a probe landed between them.
	 */
	private static double uptimeAcross(Iterable<StatusBucketRow> buckets) {
		long total = 0;
		long up = 0;
		for (StatusBucketRow bucket : buckets) {
			total += bucket.totalChecks();
			up += bucket.upChecks();
		}
		return total == 0 ? 0 : (up * 100.0) / total;
	}

	/**
	 * When the figures were last refreshed.
	 *
	 * <p>The newest probe across all services, so the page can say how current it is. Falls back
	 * to now when nothing has been probed: the answer was still assembled at this moment, and a
	 * null would make every client invent its own placeholder.
	 */
	private static Instant mostRecentCheck(Iterable<StatusCheck> checks, Instant fallback) {
		Instant newest = null;
		for (StatusCheck check : checks) {
			if (newest == null || check.checkedAt().isAfter(newest)) {
				newest = check.checkedAt();
			}
		}
		return newest == null ? fallback : newest;
	}

}
