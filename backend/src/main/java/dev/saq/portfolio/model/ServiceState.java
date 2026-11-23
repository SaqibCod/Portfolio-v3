package dev.saq.portfolio.model;

import java.util.Collection;
import java.util.Comparator;

/**
 * How one monitored project -- or the site as a whole -- is doing.
 *
 * <p>The three stored constants are persisted by name and pinned by a {@code check} constraint in
 * {@code V4__status.sql}, so renaming one needs a migration. {@link #UNKNOWN} is never stored: it
 * is what the absence of a probe result looks like, invented when a history bucket turns out
 * empty or when a service has never been reached.
 *
 * <p>The API spells these in lowercase ({@code operational}) while the column holds the uppercase
 * names, and this enum is the only place that bridges the two -- {@link #toApi()} going out,
 * {@link #fromApi(String)} coming in.
 */
public enum ServiceState {

	/** Answered successfully, inside the degraded threshold. */
	OPERATIONAL(0),

	/**
	 * Nothing has been measured. Never stored.
	 *
	 * <p>Ranked just above {@link #OPERATIONAL} rather than at either end. A service nobody has
	 * managed to probe must not be reported as healthy, but it is still better news than one
	 * measured to be slow or dead: given a down service and an unprobed one, the headline worth
	 * showing is the outage.
	 */
	UNKNOWN(1),

	/** Answered successfully, but at or beyond {@code app.status.degraded-threshold}. */
	DEGRADED(2),

	/** Did not answer, timed out, or answered with something other than 2xx. */
	DOWN(3);

	private final int severity;

	ServiceState(int severity) {
		this.severity = severity;
	}

	/** Higher is worse. Only meaningful relative to the other constants. */
	public int severity() {
		return severity;
	}

	/** True for the three states a probe can actually record. */
	public boolean isStored() {
		return this != UNKNOWN;
	}

	/** The lowercase spelling the contract uses, e.g. {@code "operational"}. */
	public String toApi() {
		return name().toLowerCase();
	}

	/**
	 * The state an API value names.
	 *
	 * @param value a lowercase state from the contract; leading and trailing space is tolerated
	 * @throws IllegalArgumentException if {@code value} is not one of the four
	 */
	public static ServiceState fromApi(String value) {
		if (value == null) {
			throw new IllegalArgumentException("state is required");
		}
		return switch (value.strip().toLowerCase()) {
			case "operational" -> OPERATIONAL;
			case "degraded" -> DEGRADED;
			case "down" -> DOWN;
			case "unknown" -> UNKNOWN;
			default -> throw new IllegalArgumentException(
					"state must be one of operational, degraded, down, unknown");
		};
	}

	/**
	 * The worst of several states, which is how both the overall banner and a history bucket are
	 * summarised.
	 *
	 * @return the most severe state present, or {@link #UNKNOWN} for an empty collection -- with
	 *         nothing to go on, nothing is known
	 */
	public static ServiceState worst(Collection<ServiceState> states) {
		return states.stream()
				.max(Comparator.comparingInt(ServiceState::severity))
				.orElse(UNKNOWN);
	}

}
