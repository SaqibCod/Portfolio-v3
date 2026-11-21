import { apiClient } from "@/lib/api/client";

import type { components } from "@/lib/api/schema";

/** The contribution graph, language shares and totals, as the contract defines them. */
export type GithubSummary = components["schemas"]["GithubSummary"];

/** Recent commits, pull requests and releases. */
export type GithubActivity = components["schemas"]["GithubActivity"];

/** One item in the "Currently building" list. */
export type GithubActivityItem = components["schemas"]["GithubActivityItem"];

/** One day of the contribution graph. */
export type ContributionDay = GithubSummary["contributions"][number];

/** One language's share of the code. */
export type TopLanguage = GithubSummary["topLanguages"][number];

/**
 * How long a server-rendered GitHub widget may be reused, in seconds.
 *
 * Fifteen minutes matches the activity cache behind the API, which is the
 * faster-moving of the two: there is no point revalidating more often than the
 * data can change. The summary is cached for six hours upstream and simply
 * returns the same figures until it refreshes.
 */
export const GITHUB_REVALIDATE_SECONDS = 900;

/**
 * How many activity items the home page asks for.
 *
 * The contract's default, deliberately, and not a number picked for the
 * layout: the API caches activity per requested size and its refresh job keeps
 * only the default warm, so asking for anything else would miss the cache and
 * send the first request after each refresh to GitHub. The export shows four
 * sample rows, which is a placeholder count rather than a design constraint.
 */
export const ACTIVITY_ITEM_COUNT = 5;

/**
 * The GitHub summary, for the server-rendered stats card.
 *
 * `next: { revalidate }` puts the response in Next's data cache, so the figures
 * are part of the static HTML and re-fetched at most once every fifteen
 * minutes rather than on every request.
 *
 * Returns `null` when the API cannot be reached, or answers 503 because it has
 * never reached GitHub. The section is built to render without it: a widget is
 * not worth taking out an indexable page for, so the caller shows a quiet
 * fallback instead.
 */
export async function fetchGithubSummary(): Promise<GithubSummary | null> {
  try {
    const { data } = await apiClient.GET("/api/v1/github/summary", {
      next: { revalidate: GITHUB_REVALIDATE_SECONDS },
    });
    return data ?? null;
  } catch {
    return null;
  }
}

/**
 * Recent activity, for the server-rendered "Recent activity" card.
 *
 * Same contract as {@link fetchGithubSummary}: cached for the ISR window, and
 * `null` rather than a thrown error when the API is unavailable.
 */
export async function fetchGithubActivity(
  limit = ACTIVITY_ITEM_COUNT,
): Promise<GithubActivity | null> {
  try {
    const { data } = await apiClient.GET("/api/v1/github/activity", {
      params: { query: { limit } },
      next: { revalidate: GITHUB_REVALIDATE_SECONDS },
    });
    return data ?? null;
  } catch {
    return null;
  }
}

/** The five shades the heatmap uses, 0 being a day with no contributions. */
export type HeatLevel = 0 | 1 | 2 | 3 | 4;

/**
 * Which shade a day gets, relative to the busiest day of the year.
 *
 * Relative rather than on fixed counts, because "a busy day" means something
 * different for every account: fixed thresholds would render a steady five-a-day
 * year as uniformly pale, and the graph is a shape to read, not a measurement.
 * The exact count is in the text alternative for anyone who needs the number.
 */
export function heatLevel(count: number, busiestDay: number): HeatLevel {
  if (count <= 0 || busiestDay <= 0) return 0;

  const share = count / busiestDay;
  if (share <= 0.25) return 1;
  if (share <= 0.5) return 2;
  if (share <= 0.75) return 3;
  return 4;
}

/** One week of the graph: up to seven days, oldest first. */
export type ContributionWeek = {
  /** The date of the first day in the column, used as a stable key and label. */
  startDate: string;
  days: ContributionDay[];
  /** Contributions across the whole column, for the week's text alternative. */
  total: number;
};

/**
 * Groups the contract's flat day list into the graph's columns.
 *
 * The API publishes one entry per day and leaves the layout to the client. The
 * design draws seven rows filled column by column, so the days are chunked by
 * seven here rather than in the component — it is a transformation with an
 * answer worth testing, not a rendering detail.
 *
 * Weeks are kept in the order the days arrive (oldest first), and a final
 * partial week is kept rather than dropped, so the newest day is always drawn.
 */
export function toContributionWeeks(days: ContributionDay[]): ContributionWeek[] {
  const weeks: ContributionWeek[] = [];

  for (let index = 0; index < days.length; index += 7) {
    const chunk = days.slice(index, index + 7);
    weeks.push({
      startDate: chunk[0].date,
      days: chunk,
      total: chunk.reduce((sum, day) => sum + day.count, 0),
    });
  }

  return weeks;
}

/** The highest single-day count, which every shade is measured against. */
export function busiestDay(days: ContributionDay[]): number {
  return days.reduce((highest, day) => Math.max(highest, day.count), 0);
}
