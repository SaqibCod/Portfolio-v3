package dev.saq.portfolio.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/status/{serviceSlug}/history} — one project's uptime over a window.
 *
 * <p>Mirrors the {@code StatusHistory} schema in {@code docs/api/portfolio-api.yaml}.
 * {@code points} always carries the window's full bucket count — 288 for {@code 24h}, 168 for
 * {@code 7d}, 30 for {@code 30d} — so a client can draw the chart without reasoning about gaps:
 * a stretch nobody probed is present, as an {@code unknown} point.
 *
 * @param uptime percentage of probes in the window that the service answered, {@code 0} when
 *               there were none
 */
public record StatusHistoryResponse(
		String slug,
		String window,
		double uptime,
		List<Point> points) {

	/**
	 * One bucket.
	 *
	 * @param timestamp         the start of the bucket
	 * @param avgResponseTimeMs mean round trip of the probes in the bucket that completed, or
	 *                          {@code null} when none did
	 */
	public record Point(Instant timestamp, String state, Integer avgResponseTimeMs) {
	}

}
