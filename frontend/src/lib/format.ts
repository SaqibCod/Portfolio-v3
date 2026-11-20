import { siteConfig } from "@/lib/site-config";

/** BCP 47 tag derived from the site's Open Graph locale, e.g. "en-US". */
const locale = siteConfig.locale.replace("_", "-");

const dateFormatter = new Intl.DateTimeFormat(locale, {
  year: "numeric",
  month: "short",
  day: "numeric",
  // Content dates are plain calendar days; without a fixed zone the formatter
  // would shift them by the machine's offset and could render the day before.
  timeZone: "UTC",
});

/**
 * Formats a `YYYY-MM-DD` content date for display.
 *
 * `Intl` rather than a hand-rolled format, and a fixed locale and time zone
 * rather than the environment's, so the string is identical on the server, in
 * the browser and across build machines — a locale-dependent date is the
 * classic hydration mismatch.
 */
export function formatDate(isoDate: string): string {
  return dateFormatter.format(new Date(`${isoDate}T00:00:00Z`));
}

/**
 * Formats an ISO 8601 timestamp from the API as a calendar day.
 *
 * Absolute rather than relative ("3 days ago"), and in UTC rather than the
 * reader's zone, because guestbook entries are server-rendered: a relative or
 * zone-dependent string would be computed once on the server and then disagree
 * with the browser, which is the classic hydration mismatch.
 *
 * @param isoTimestamp e.g. `2026-11-20T10:15:00Z`
 */
export function formatTimestampDate(isoTimestamp: string): string {
  return dateFormatter.format(new Date(isoTimestamp));
}

const relativeFormatter = new Intl.RelativeTimeFormat(locale, {
  numeric: "auto",
});

const RELATIVE_UNITS: readonly [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 365 * 24 * 60 * 60],
  ["month", 30 * 24 * 60 * 60],
  ["day", 24 * 60 * 60],
  ["hour", 60 * 60],
  ["minute", 60],
];

/**
 * Formats an ISO 8601 timestamp as "3 days ago", for the moderation queue.
 *
 * Safe here, and only here: `/admin` renders nothing until a fetch in the
 * browser has returned, so there is no server-rendered string for this to
 * disagree with. Everything server-rendered uses {@link formatTimestampDate}.
 */
export function formatRelativeTime(
  isoTimestamp: string,
  now: Date = new Date(),
): string {
  const elapsedSeconds = (new Date(isoTimestamp).getTime() - now.getTime()) / 1000;

  for (const [unit, unitSeconds] of RELATIVE_UNITS) {
    if (Math.abs(elapsedSeconds) >= unitSeconds) {
      return relativeFormatter.format(
        Math.round(elapsedSeconds / unitSeconds),
        unit,
      );
    }
  }
  return relativeFormatter.format(Math.round(elapsedSeconds), "second");
}
