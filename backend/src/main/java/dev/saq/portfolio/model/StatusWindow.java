package dev.saq.portfolio.model;

import java.time.Duration;
import java.time.Instant;

/**
 * A history window the contract offers, and the bucket size it is drawn at.
 *
 * <p>Each window is a fixed number of fixed-width buckets, so a response always carries the same
 * number of points: 288 five-minute buckets over 24 hours, 168 hourly buckets over 7 days, 30
 * daily buckets over 30 days.
 *
 * <p>Buckets are aligned to the Unix epoch rather than to the moment of the request or to a
 * calendar day. That is what lets {@link #bucketStart(Instant)} here and PostgreSQL's
 * {@code date_bin(stride, checked_at, timestamptz 'epoch')} agree on which bucket a check belongs
 * to without either one knowing the other's time zone -- the service builds the timeline, the
 * database fills it in, and nothing has to reconcile two different ideas of where a day starts.
 */
public enum StatusWindow {

	/** 24 hours in 5-minute buckets. */
	LAST_24H("24h", Duration.ofMinutes(5), 288, "5 minutes"),

	/** 7 days in hourly buckets. */
	LAST_7D("7d", Duration.ofHours(1), 168, "1 hour"),

	/** 30 days in daily buckets. */
	LAST_30D("30d", Duration.ofDays(1), 30, "1 day");

	/** The window the contract uses when the parameter is absent. */
	public static final StatusWindow DEFAULT = LAST_24H;

	private final String apiValue;

	private final Duration stride;

	private final int buckets;

	private final String pgInterval;

	StatusWindow(String apiValue, Duration stride, int buckets, String pgInterval) {
		this.apiValue = apiValue;
		this.stride = stride;
		this.buckets = buckets;
		this.pgInterval = pgInterval;
	}

	/** The spelling the contract uses, e.g. {@code "24h"}. */
	public String toApi() {
		return apiValue;
	}

	/** How wide one bucket is. */
	public Duration stride() {
		return stride;
	}

	/** How many buckets a response for this window carries, always. */
	public int buckets() {
		return buckets;
	}

	/**
	 * The stride as a PostgreSQL {@code interval} literal.
	 *
	 * <p>Bound as a parameter and cast in SQL rather than concatenated, so this string is never
	 * part of a statement's text.
	 */
	public String pgInterval() {
		return pgInterval;
	}

	/** The start of the bucket an instant falls into, epoch-aligned. */
	public Instant bucketStart(Instant instant) {
		long strideSeconds = stride.getSeconds();
		return Instant.ofEpochSecond(
				Math.floorDiv(instant.getEpochSecond(), strideSeconds) * strideSeconds);
	}

	/**
	 * The start of the oldest bucket in the window ending at {@code now}.
	 *
	 * <p>The newest bucket is the one {@code now} is in, partial though it is, so the window
	 * always reaches right up to the present.
	 */
	public Instant windowStart(Instant now) {
		return bucketStart(now).minus(stride.multipliedBy(buckets - 1L));
	}

	/**
	 * The window an API value names.
	 *
	 * @param value {@code 24h}, {@code 7d} or {@code 30d}; leading and trailing space is tolerated
	 * @throws IllegalArgumentException if {@code value} is none of them. Registered as a web
	 *                                  converter in
	 *                                  {@link dev.saq.portfolio.config.WebMvcConfig}, where Spring
	 *                                  turns that into a 400 rather than a 500
	 */
	public static StatusWindow fromApi(String value) {
		if (value == null) {
			throw new IllegalArgumentException("window is required");
		}
		return switch (value.strip().toLowerCase()) {
			case "24h" -> LAST_24H;
			case "7d" -> LAST_7D;
			case "30d" -> LAST_30D;
			default -> throw new IllegalArgumentException("window must be one of 24h, 7d, 30d");
		};
	}

}
