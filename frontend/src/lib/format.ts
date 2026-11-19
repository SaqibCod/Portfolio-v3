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
