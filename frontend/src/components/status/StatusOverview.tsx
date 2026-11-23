"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
} from "react";

import { ServiceRow } from "@/components/status/ServiceRow";
import { StateIcon } from "@/components/status/StateIcon";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { cn } from "@/lib/cn";
import { formatRelativeTime } from "@/lib/format";
import {
  bannerCopy,
  DEFAULT_HISTORY_WINDOW,
  fetchLiveStatusOverview,
  formatCheckedAtUtc,
  HISTORY_WINDOWS,
  STATE_LEGEND,
  STATUS_REVALIDATE_SECONDS,
  type HistoryWindow,
  type ServiceState,
  type StatusHistory,
  type StatusOverview as StatusOverviewData,
} from "@/lib/status";

/** `.banner` and `.bicon` tones, plus the matching legend dot. */
const stateTones: Record<
  ServiceState,
  { wash: string; disc: string; dot: "ok" | "warn" | "err" | "off" }
> = {
  operational: { wash: "bg-ok", disc: "bg-ok", dot: "ok" },
  degraded: { wash: "bg-warn", disc: "bg-warn", dot: "warn" },
  down: { wash: "bg-err", disc: "bg-err", dot: "err" },
  unknown: { wash: "bg-off", disc: "bg-off", dot: "off" },
};

/** How often the page re-reads the API while the tab is in front. */
const POLL_INTERVAL_MS = STATUS_REVALIDATE_SECONDS * 1000;

function RefreshIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={spinning ? "motion-safe:animate-spin" : undefined}
    >
      <path d="M20 11a8 8 0 1 0-2.3 5.7" />
      <path d="M20 4v7h-7" />
    </svg>
  );
}

type StatusOverviewProps = {
  /** Server-rendered overview, or `null` when the API could not be reached. */
  initialOverview: StatusOverviewData | null;
  /** 30-day history per slug, for each row's summary strip. */
  summaries: Record<string, StatusHistory | null>;
  /** Case-study path per slug, where a case study exists. */
  caseStudies: Record<string, string>;
};

/**
 * The live half of the status page: the overall banner, the legend, and one row
 * per service.
 *
 * A client component that is nonetheless fully server-rendered. Everything a
 * crawler or a reader without JavaScript needs — the banner's wording, each
 * service's state in words, its uptime and its 30-day strip — is in the HTML,
 * because it all comes from `initialOverview`. What JavaScript adds is only
 * freshness: a poll while the tab is visible, a Refresh button, and the other
 * two history windows.
 *
 * The poll stops when the tab is hidden and runs once on the way back. A status
 * page is the kind of thing people leave open during an incident, and polling a
 * background tab every minute for hours would spend the API's time and the
 * reader's battery on an answer nobody is looking at.
 */
export function StatusOverview({
  initialOverview,
  summaries,
  caseStudies,
}: StatusOverviewProps) {
  const [overview, setOverview] = useState(initialOverview);
  const [refreshing, setRefreshing] = useState(false);
  const { selectedWindow, setSelectedWindow, openSlugs, toggleSlug } =
    usePanelState(initialOverview);

  const mounted = useHydrated();

  const refresh = useCallback(async () => {
    setRefreshing(true);
    const next = await fetchLiveStatusOverview();
    if (next) setOverview(next);
    setRefreshing(false);
  }, []);

  useEffect(() => {
    const poll = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    const interval = window.setInterval(poll, POLL_INTERVAL_MS);
    document.addEventListener("visibilitychange", poll);

    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", poll);
    };
  }, [refresh]);

  const unreachable = overview === null;
  const overall: ServiceState = overview?.overall ?? "unknown";
  const tone = stateTones[overall];
  const copy = unreachable
    ? {
        title: "Status Unavailable",
        detail: "The monitor is not responding, so there is no live data to show.",
      }
    : bannerCopy(overview);

  return (
    <>
      <Container className="relative">
        <div
          role="status"
          aria-live="polite"
          className={cn(
            "relative mt-8 flex flex-wrap items-center gap-6 overflow-hidden",
            "rounded-xl border border-line bg-bg2 p-6 md:mt-12 md:rounded-2xl md:px-9 md:py-8",
          )}
        >
          <span
            aria-hidden="true"
            className={cn("absolute inset-0 opacity-10", tone.wash)}
          />

          <span
            aria-hidden="true"
            className={cn(
              "relative flex size-13 shrink-0 items-center justify-center rounded-full",
              "text-white md:size-16",
              tone.disc,
            )}
          >
            <StateIcon state={overall} variant="banner" />
          </span>

          <div className="relative min-w-0 flex-[1_1_320px]">
            <h2 className="display m-0 text-[clamp(24px,3.6vw,48px)]">
              {copy.title}
            </h2>
            <p className="mt-2.5 text-[15px] leading-normal text-mute">
              {copy.detail}
            </p>
          </div>

          <div className="relative flex w-full flex-wrap items-center gap-3 md:w-auto">
            <Badge dot={tone.dot} live={overall === "operational"}>
              {checkedLabel(overview, mounted, refreshing)}
            </Badge>
            <Button
              onClick={() => void refresh()}
              disabled={refreshing}
              className="max-md:flex-1"
            >
              <RefreshIcon spinning={refreshing} />
              Refresh
            </Button>
          </div>
        </div>

        <ul
          aria-label="Legend"
          className="mt-6 flex list-none flex-wrap gap-x-4.5 gap-y-2 font-mono text-chip text-mute"
        >
          {STATE_LEGEND.map(({ state, label }) => (
            <li key={state} className="inline-flex items-center gap-2">
              <i
                aria-hidden="true"
                className={cn(
                  "size-2 shrink-0 rounded-full",
                  stateTones[state].disc,
                  state === "unknown" && "opacity-70",
                )}
              />
              {label}
            </li>
          ))}
        </ul>
      </Container>

      <Container className="pt-14 pb-20 md:pt-14 md:pb-30">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <h2 className="display m-0 text-[clamp(32px,4vw,52px)]">Services</h2>
          <p className="eyebrow m-0">
            30-day history · open a row for details
          </p>
        </div>

        {unreachable ? (
          <div
            role="alert"
            className="flex flex-col items-center gap-3 rounded-card border border-dashed border-line px-6 py-10 text-center"
          >
            <h3 className="display m-0 text-[24px]">Can’t Reach the Monitor</h3>
            <p className="m-0 max-w-[440px] leading-relaxed text-mute">
              The status API didn’t respond, so live data isn’t available right
              now. This doesn’t necessarily mean the projects are down — try
              again in a moment.
            </p>
            <Button variant="primary" onClick={() => void refresh()}>
              Try Again
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-3.5">
            {overview.services.map((service) => (
              <ServiceRow
                key={service.slug}
                service={service}
                summary={summaries[service.slug] ?? null}
                expanded={openSlugs.includes(service.slug)}
                onToggle={() => toggleSlug(service.slug)}
                window={selectedWindow}
                onWindowChange={setSelectedWindow}
                caseStudyHref={caseStudies[service.slug] ?? null}
                panelId={`status-detail-${service.slug}`}
              />
            ))}
          </div>
        )}
      </Container>
    </>
  );
}

/** `useSyncExternalStore` needs a subscribe function; there is nothing to watch. */
const noSubscription = () => () => {};

/**
 * False while rendering on the server and through hydration, true afterwards.
 *
 * For the one value on this page that cannot be computed identically in both
 * places: "checked 2 minutes ago" depends on which clock is asked. The server
 * renders the absolute UTC time, and this flips to the relative wording once
 * there is only one clock left.
 *
 * `useSyncExternalStore` rather than a `useState` + `useEffect` pair, which
 * would be a setState inside an effect body — a cascading render, and something
 * the lint config rejects outright.
 */
function useHydrated(): boolean {
  return useSyncExternalStore(
    noSubscription,
    () => true,
    () => false,
  );
}

/** The chip beside Refresh: how fresh the figures on screen are. */
function checkedLabel(
  overview: StatusOverviewData | null,
  mounted: boolean,
  refreshing: boolean,
): string {
  if (refreshing) return "Checking…";
  if (!overview) return "No data";
  return mounted
    ? `Checked ${formatRelativeTime(overview.checkedAt)}`
    : `Checked at ${formatCheckedAtUtc(overview.checkedAt)}`;
}

/** Fired after a `replaceState`, so the store below hears our own writes. */
const SEARCH_CHANGED = "status:searchchanged";

function subscribeToSearch(onChange: () => void) {
  globalThis.addEventListener("popstate", onChange);
  globalThis.addEventListener(SEARCH_CHANGED, onChange);
  return () => {
    globalThis.removeEventListener("popstate", onChange);
    globalThis.removeEventListener(SEARCH_CHANGED, onChange);
  };
}

/**
 * Writes the panel state into the address bar without navigating.
 *
 * `history.replaceState` rather than the router: there is no new page here, and
 * opening a disclosure should not land in the back button — that would bury
 * whatever page the reader arrived from under a row they expanded.
 */
function writeSearch(params: URLSearchParams) {
  const query = params.toString();
  globalThis.history.replaceState(
    null,
    "",
    query ? `?${query}` : globalThis.location.pathname,
  );
  globalThis.dispatchEvent(new Event(SEARCH_CHANGED));
}

/**
 * Which rows are open and which window they show, mirrored into the URL.
 *
 * So a link to the page can land on a particular project and window — which is
 * what someone sharing "look at this" during an incident actually wants, rather
 * than the top of a page the recipient then has to navigate.
 *
 * Deliberately not `useSearchParams`. That hook makes the client tree up to the
 * nearest Suspense boundary client-side rendered, and this tree is the entire
 * status list — the banner's wording, every service's state and uptime, every
 * summary strip. Trading all of that out of the HTML for a shareable query
 * string would be backwards on a page whose job is to be readable.
 *
 * So the URL is an external store instead: the server snapshot is empty, which
 * is what prerenders, and the real query string arrives at hydration. The
 * subscription covers the back button as well as this component's own writes.
 */
function usePanelState(initialOverview: StatusOverviewData | null) {
  const search = useSyncExternalStore(
    subscribeToSearch,
    () => globalThis.location.search,
    () => "",
  );
  const searchParams = useMemo(() => new URLSearchParams(search), [search]);

  const windowParam = searchParams.get("window");
  const selectedWindow: HistoryWindow = HISTORY_WINDOWS.includes(
    windowParam as HistoryWindow,
  )
    ? (windowParam as HistoryWindow)
    : DEFAULT_HISTORY_WINDOW;

  const serviceParam = searchParams.get("service");
  const firstSlug = initialOverview?.services[0]?.slug;

  // Absent `service` opens the first row, as the export shows. An explicitly
  // empty `service=` is a deliberate "all closed" and is honoured as one.
  //
  // Memoised because `toggleSlug` closes over it: a fresh array every render
  // would give that callback a new identity on every render too, and every row
  // would re-render whenever anything on the page changed.
  const openSlugs = useMemo(
    () =>
      serviceParam === null
        ? firstSlug
          ? [firstSlug]
          : []
        : serviceParam.split(",").filter(Boolean),
    [serviceParam, firstSlug],
  );

  const write = useCallback(
    (changes: { window?: HistoryWindow; services?: string[] }) => {
      const next = new URLSearchParams(searchParams.toString());

      if (changes.window) next.set("window", changes.window);
      if (changes.services) next.set("service", changes.services.join(","));

      writeSearch(next);
    },
    [searchParams],
  );

  return {
    selectedWindow,
    openSlugs,
    setSelectedWindow: useCallback(
      (window: HistoryWindow) => write({ window }),
      [write],
    ),
    toggleSlug: useCallback(
      (slug: string) =>
        write({
          services: openSlugs.includes(slug)
            ? openSlugs.filter((open) => open !== slug)
            : [...openSlugs, slug],
        }),
      [openSlugs, write],
    ),
  };
}
