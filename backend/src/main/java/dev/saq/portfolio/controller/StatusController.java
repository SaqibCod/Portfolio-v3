package dev.saq.portfolio.controller;

import java.time.Duration;

import dev.saq.portfolio.dto.response.StatusHistoryResponse;
import dev.saq.portfolio.dto.response.StatusOverviewResponse;
import dev.saq.portfolio.model.StatusWindow;
import dev.saq.portfolio.service.StatusService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two read-only endpoints behind the status page.
 *
 * <p>Deliberately not rate limited. Every other public endpoint is, but the contract documents no
 * 429 here and the reason is the point of the page: the one moment it matters is an outage, when
 * traffic spikes and people refresh. Throttling the page that explains the outage would be the
 * wrong trade, and both responses are cheap — three indexed aggregates, cacheable for a minute.
 */
@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

	/**
	 * One probe interval's worth of caching, so a cached copy never outlives the data behind it.
	 *
	 * <p>Shorter than {@code app.status.interval}'s default on purpose: the interval is
	 * configurable and a visitor refreshing during an incident should pick up the next sweep
	 * quickly, so this is pinned to a minute rather than derived.
	 */
	private static final Duration MAX_AGE = Duration.ofMinutes(1);

	private final StatusService statusService;

	public StatusController(StatusService statusService) {
		this.statusService = statusService;
	}

	/**
	 * Current state of every monitored project.
	 *
	 * <p>Always a 200, including when nothing is monitored — an empty list is an answer, not an
	 * error, and the site uses exactly that to decide whether to publish its status page.
	 */
	@GetMapping
	public ResponseEntity<StatusOverviewResponse> status() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
				.body(statusService.getOverview());
	}

	/**
	 * Uptime and response-time history for one project.
	 *
	 * @param window which window to read. Bound through {@link StatusWindow#fromApi} by the
	 *               converter registered in {@link dev.saq.portfolio.config.WebMvcConfig}, so an
	 *               unsupported value is a 400 rather than a 500
	 * @return 404 if nothing by that slug is monitored
	 */
	@GetMapping("/{serviceSlug}/history")
	public ResponseEntity<StatusHistoryResponse> history(
			@PathVariable String serviceSlug,
			@RequestParam(defaultValue = "24h") StatusWindow window) {

		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
				.body(statusService.getHistory(serviceSlug, window));
	}

}
