import { apiClient } from "@/lib/api/client";

import type { components } from "@/lib/api/schema";

/** The current state of every monitored project, as the contract defines it. */
export type StatusOverview = components["schemas"]["StatusOverview"];

/** One monitored project in the overview. */
export type ServiceStatus = StatusOverview["services"][number];

/** One project's uptime over a window. */
export type StatusHistory = components["schemas"]["StatusHistory"];

/** One history bucket. */
export type StatusPoint = StatusHistory["points"][number];

/** `operational | degraded | down | unknown`. */
export type ServiceState = components["schemas"]["ServiceState"];

/** `24h | 7d | 30d`. */
export type HistoryWindow = StatusHistory["window"];

/**
 * How long a server-rendered status response may be reused, in seconds.
 *
 * Matches the API's own `Cache-Control: max-age=60`, which in turn tracks the
 * probe interval — there is no point revalidating faster than new probes can
 * land. The page also polls while it is visible, so a reader watching an
 * incident is never waiting on this.
 */
export const STATUS_REVALIDATE_SECONDS = 60;

/**
 * How long the "is there a status page at all?" answer may be reused.
 *
 * Far longer than the data itself, because it answers a different question: not
 * "how are the projects doing" but "does the owner monitor anything". That
 * changes when a project is deployed and a service is added to the backend's
 * `app.status.services` — which is to say, almost never.
 *
 * It is deliberately not instant. This value is read by the root layout, so a
 * short window here would put every page on the site on a short revalidation
 * cycle for the sake of a nav link.
 */
export const STATUS_PUBLISHED_REVALIDATE_SECONDS = 3600;

/** The windows the contract offers, in the order the design's tabs show them. */
export const HISTORY_WINDOWS: readonly HistoryWindow[] = ["24h", "7d", "30d"];

/** The window the detail panel opens on, matching the contract's default. */
export const DEFAULT_HISTORY_WINDOW: HistoryWindow = "24h";

/**
 * The window each service row's summary strip is drawn from.
 *
 * Thirty daily buckets, which is both what the design draws in the row and
 * what `uptime30d` beside it is measured over — so the bar and the number
 * never tell different stories.
 */
export const SUMMARY_WINDOW: HistoryWindow = "30d";

/** How many points each window carries, straight from the contract. */
export const BUCKET_COUNTS: Record<HistoryWindow, number> = {
  "24h": 288,
  "7d": 168,
  "30d": 30,
};

/** The label beneath each window's chart, naming where the axis starts. */
export const WINDOW_AXIS_LABELS: Record<HistoryWindow, string> = {
  "24h": "24h ago",
  "7d": "7 days ago",
  "30d": "30 days ago",
};

/**
 * The current status of everything monitored.
 *
 * Returns `null` when the API cannot be reached — which is a different answer
 * from an empty `services` list, and the two lead to opposite behaviour. An
 * empty list is the API stating that nothing is monitored; `null` is the API
 * failing to state anything. See {@link statusPageIsPublished}.
 */
export async function fetchStatusOverview(): Promise<StatusOverview | null> {
  try {
    const { data } = await apiClient.GET("/api/v1/status", {
      next: { revalidate: STATUS_REVALIDATE_SECONDS },
    });
    return data ?? null;
  } catch {
    return null;
  }
}

/**
 * The current status, bypassing every cache.
 *
 * For the page's own polling and its Refresh button. The cached fetch above
 * would be answered from the browser's HTTP cache for up to a minute, which is
 * right for a page load and wrong for someone pressing Refresh during an
 * incident — the one moment the answer has to be current.
 */
export async function fetchLiveStatusOverview(): Promise<StatusOverview | null> {
  try {
    const { data } = await apiClient.GET("/api/v1/status", { cache: "no-store" });
    return data ?? null;
  } catch {
    return null;
  }
}

/**
 * One project's history over a window.
 *
 * `null` covers both an unreachable API and a 404 for a slug nobody monitors.
 * The caller draws an empty chart either way: a missing strip is not worth
 * taking the page down for.
 */
export async function fetchStatusHistory(
  slug: string,
  window: HistoryWindow = DEFAULT_HISTORY_WINDOW,
): Promise<StatusHistory | null> {
  try {
    const { data } = await apiClient.GET(
      "/api/v1/status/{serviceSlug}/history",
      {
        params: { path: { serviceSlug: slug }, query: { window } },
        next: { revalidate: STATUS_REVALIDATE_SECONDS },
      },
    );
    return data ?? null;
  } catch {
    return null;
  }
}

/**
 * Whether the site should advertise a status page.
 *
 * Only an API that answers with services published one. The two negative cases
 * are different and both end up here:
 *
 * - **Nothing monitored.** The owner has deployed nothing yet, so a status page
 *   would be an empty page. The route 404s and the link is dropped.
 * - **API unreachable.** We do not know, so we do not advertise. The `/status`
 *   route itself still renders — with the design's "can't reach the monitor"
 *   panel — because a backend outage must not un-publish a real page. Once the
 *   API answers again the link reappears on its own.
 *
 * Adding a project is therefore a change to the backend's `app.status.services`
 * and nothing else: no deploy of this app, no environment variable, no code.
 */
export async function statusPageIsPublished(): Promise<boolean> {
  try {
    const { data } = await apiClient.GET("/api/v1/status", {
      next: { revalidate: STATUS_PUBLISHED_REVALIDATE_SECONDS },
    });
    return (data?.services.length ?? 0) > 0;
  } catch {
    return false;
  }
}

/** The word shown beside each state, so the colour is never the only signal. */
export const STATE_LABELS: Record<ServiceState, string> = {
  operational: "Operational",
  degraded: "Degraded",
  down: "Down",
  unknown: "Unknown",
};

/** The design's legend, in its order. `unknown` names both of its meanings. */
export const STATE_LEGEND: readonly { state: ServiceState; label: string }[] = [
  { state: "operational", label: "Operational" },
  { state: "degraded", label: "Degraded" },
  { state: "down", label: "Down" },
  { state: "unknown", label: "Unknown / no data" },
];

/**
 * The host a service's row shows under its name.
 *
 * Falls back to the raw string rather than throwing: the value comes from the
 * API, and a malformed URL is not worth an error boundary over.
 */
export function hostFromUrl(url: string | undefined): string {
  if (!url) return "";
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

/**
 * A response time for display.
 *
 * `down` reads "timeout" rather than "—": the probe did run, it just never got
 * an answer, and that is a more useful thing to say than nothing. Only a
 * service nobody has measured gets the dash.
 */
export function formatResponseTime(
  responseTimeMs: number | null | undefined,
  state: ServiceState,
): string {
  if (state === "down") return "timeout";
  if (responseTimeMs === null || responseTimeMs === undefined) return "—";
  return `${responseTimeMs.toLocaleString("en-US")} ms`;
}

/**
 * An uptime percentage for display.
 *
 * A service that has never been probed shows "—" rather than "0%", which would
 * read as a month of downtime instead of an absence of data. Trailing zeroes
 * are dropped, so 100 is "100%" and not "100.00%".
 */
export function formatUptime(
  percent: number,
  state: ServiceState = "operational",
): string {
  if (state === "unknown") return "—";
  return `${Number(percent.toFixed(2))}%`;
}

/** The headline and supporting line for the overall banner. */
export type BannerCopy = {
  title: string;
  detail: string;
};

/**
 * The banner's copy, derived from the services rather than written per state.
 *
 * The design shows one example of each state with a hand-written sentence; the
 * sentences here say the same things from the data, so a reader is told which
 * project is affected rather than that "a service" is.
 */
export function bannerCopy(overview: StatusOverview): BannerCopy {
  const { overall, services } = overview;
  const affected = services.filter((service) => service.state === overall);
  const names = affected.map((service) => service.name);

  if (overall === "operational") {
    return {
      title: "All Systems Operational",
      detail:
        services.length === 1
          ? `${services[0].name} is up and responding normally.`
          : `All ${services.length} services are up and responding normally.`,
    };
  }

  if (overall === "degraded") {
    return {
      title: "Partial Degradation",
      detail: `${joinNames(names)} ${names.length === 1 ? "is" : "are"} responding slowly. ${
        services.length > affected.length
          ? "Everything else is fine."
          : "Nothing else is affected."
      }`,
    };
  }

  if (overall === "down") {
    return {
      title:
        names.length === 1 ? `${names[0]} Is Down` : `${names.length} Services Are Down`,
      detail: `${joinNames(names)} stopped responding. ${
        services.length > affected.length
          ? "The other services are unaffected."
          : "Every monitored service is affected."
      }`,
    };
  }

  return {
    title: "Status Unknown",
    detail:
      affected.length === services.length
        ? "Nothing has been probed yet, so there is no state to report."
        : `${joinNames(names)} ${names.length === 1 ? "has" : "have"} not been probed yet.`,
  };
}

/** "A", "A and B", "A, B and C". */
function joinNames(names: string[]): string {
  if (names.length === 0) return "No service";
  if (names.length === 1) return names[0];
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

/**
 * How many buckets fell into each state.
 *
 * This is what makes a bar chart readable without seeing colour: the same
 * figures are printed as text beside the strip, so "two degraded buckets in the
 * last 30 days" is a sentence rather than a shade someone has to distinguish.
 */
export function countStates(points: StatusPoint[]): Record<ServiceState, number> {
  const counts: Record<ServiceState, number> = {
    operational: 0,
    degraded: 0,
    down: 0,
    unknown: 0,
  };
  for (const point of points) counts[point.state] += 1;
  return counts;
}

/** The text alternative for a bar strip: every state that actually occurs. */
export function describeStates(points: StatusPoint[]): string {
  const counts = countStates(points);
  const parts = (Object.keys(counts) as ServiceState[])
    .filter((state) => counts[state] > 0)
    .map((state) => `${counts[state]} ${STATE_LABELS[state].toLowerCase()}`);

  return parts.length === 0 ? "No data" : parts.join(" · ");
}

const checkedAtFormatter = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
  timeZone: "UTC",
});

/**
 * The "checked at" label the server renders, as a fixed-zone wall clock time.
 *
 * Absolute and in UTC on purpose. The banner is inside a client component, so
 * its first render happens on the server too, and a relative label ("1 minute
 * ago") computed there would disagree with the browser a moment later — the
 * classic hydration mismatch. The component swaps in the relative wording after
 * mounting, once there is a single clock to read.
 */
export function formatCheckedAtUtc(isoTimestamp: string): string {
  return `${checkedAtFormatter.format(new Date(isoTimestamp))} UTC`;
}

/** The width and height the response-time sparkline is drawn in. */
export const SPARKLINE_VIEWBOX = { width: 600, height: 96 } as const;

/**
 * The sparkline's `points` attribute, or `null` when there is nothing to draw.
 *
 * Buckets with no measurement are skipped rather than plotted at zero, which
 * would draw a cliff down to the axis every time a probe failed. Scaled to the
 * series' own maximum, because response times differ by an order of magnitude
 * between projects and a shared scale would flatten the faster one into a line.
 */
export function sparklinePoints(points: StatusPoint[]): string | null {
  const measured = points
    .map((point, index) => ({ index, value: point.avgResponseTimeMs }))
    .filter(
      (entry): entry is { index: number; value: number } =>
        entry.value !== null && entry.value !== undefined,
    );

  if (measured.length < 2) return null;

  const { width, height } = SPARKLINE_VIEWBOX;
  const padding = 6;
  const slowest = Math.max(...measured.map((entry) => entry.value));
  const lastIndex = Math.max(points.length - 1, 1);
  const span = height - padding * 2;

  return measured
    .map((entry) => {
      const x = (entry.index / lastIndex) * width;
      // Inverted: SVG y grows downwards, and a slower response should sit higher.
      const y = height - padding - (slowest === 0 ? 0 : (entry.value / slowest) * span);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
}

/**
 * The average response time across a window, for the detail panel's figure.
 *
 * Averaged over the buckets that measured something, so a window containing an
 * outage reports how fast the service was when it answered rather than being
 * dragged toward zero by the gaps.
 */
export function averageResponseTime(points: StatusPoint[]): number | null {
  const measured = points
    .map((point) => point.avgResponseTimeMs)
    .filter((value): value is number => value !== null && value !== undefined);

  if (measured.length === 0) return null;
  return Math.round(measured.reduce((sum, value) => sum + value, 0) / measured.length);
}
