package dev.saq.portfolio.dto.internal;

/**
 * How many probes of one service landed in a window, and how many of them found it answering.
 *
 * <p>Counts rather than a percentage: dividing in SQL would mean deciding there how to round and
 * what an empty window means, and both of those are the service's business. A window with no
 * checks produces no row at all, which is how "never probed" stays distinguishable from "probed
 * and down".
 *
 * @param serviceSlug  which service the counts belong to
 * @param totalChecks  every stored check in the window
 * @param upChecks     the checks that answered -- operational or degraded, since a slow service
 *                     is still a service that served the request
 */
public record StatusUptimeRow(String serviceSlug, long totalChecks, long upChecks) {

	/**
	 * Uptime as a percentage, {@code 0} when nothing was checked.
	 *
	 * <p>Callers that need to tell "100% of nothing" from real data should look at
	 * {@link #totalChecks} rather than at this number.
	 */
	public double uptimePercent() {
		return totalChecks == 0 ? 0 : (upChecks * 100.0) / totalChecks;
	}

}
