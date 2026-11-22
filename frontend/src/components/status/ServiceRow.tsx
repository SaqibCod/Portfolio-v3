"use client";

import { useEffect, useState } from "react";

import { HistoryWindowTabs } from "@/components/status/HistoryWindowTabs";
import { ResponseSparkline } from "@/components/status/ResponseSparkline";
import { StateIcon } from "@/components/status/StateIcon";
import { UptimeBars } from "@/components/status/UptimeBars";
import { ButtonLink } from "@/components/ui/Button";
import { cn } from "@/lib/cn";
import {
  averageResponseTime,
  describeStates,
  fetchStatusHistory,
  formatResponseTime,
  formatUptime,
  hostFromUrl,
  STATE_LABELS,
  SUMMARY_WINDOW,
  WINDOW_AXIS_LABELS,
  type HistoryWindow,
  type ServiceState,
  type ServiceStatus,
  type StatusHistory,
} from "@/lib/status";

/** `.sico` — the 40px disc beside a service's name. */
const discTones: Record<ServiceState, string> = {
  operational: "bg-ok",
  degraded: "bg-warn",
  down: "bg-err",
  unknown: "bg-off",
};

function ChevronIcon() {
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
    >
      <path d="M6 9l6 6 6-6" />
    </svg>
  );
}

type ServiceRowProps = {
  service: ServiceStatus;
  /**
   * The 30-day history behind the row's summary strip, fetched on the server so
   * the strip is in the page's HTML rather than appearing after a round trip.
   */
  summary: StatusHistory | null;
  expanded: boolean;
  onToggle: () => void;
  window: HistoryWindow;
  onWindowChange: (window: HistoryWindow) => void;
  /** Case study for this project, when one exists under `/projects`. */
  caseStudyHref: string | null;
  /** Ties the disclosure button to the panel it opens. */
  panelId: string;
};

/**
 * `.svc` from the status export: one service as a row that opens into a chart.
 *
 * The row itself is server-rendered content — name, state in words, host,
 * response time, 30-day uptime and the summary strip are all in the HTML a
 * crawler sees. Only the expanded panel's other windows are fetched, and only
 * once someone asks for one.
 *
 * The strip inside the button is marked decorative. Its label would otherwise
 * be folded into the button's accessible name, which already says "githMind,
 * Operational, githmind.example.com, Response 182 ms, Uptime 30d 99.82%" — the
 * strip is a picture of the number already in that sentence.
 */
export function ServiceRow({
  service,
  summary,
  expanded,
  onToggle,
  window,
  onWindowChange,
  caseStudyHref,
  panelId,
}: ServiceRowProps) {
  const history = useWindowHistory(service.slug, window, summary, expanded);
  const points = history?.points ?? [];
  const averageMs = averageResponseTime(points);

  return (
    <article
      className={cn(
        "overflow-hidden rounded-card border bg-bg2",
        expanded ? "border-fg" : "border-line",
      )}
    >
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        aria-controls={panelId}
        className={cn(
          "group grid w-full cursor-pointer touch-manipulation items-center gap-x-3 gap-y-3.5",
          "border-0 bg-transparent p-4.5 text-left text-fg",
          "grid-cols-[minmax(0,1fr)_40px]",
          "md:grid-cols-[minmax(0,1.3fr)_minmax(0,2fr)_120px_110px_44px] md:gap-6 md:px-6 md:py-5",
        )}
      >
        <span className="flex min-w-0 items-center gap-3.5">
          <span
            aria-hidden="true"
            className={cn(
              "flex size-10 shrink-0 items-center justify-center rounded-full text-white",
              discTones[service.state],
            )}
          >
            <StateIcon state={service.state} />
          </span>
          <span className="min-w-0">
            <b className="display block text-[22px] leading-none normal-case">
              {service.name}
            </b>
            <small className="mt-1.5 block truncate font-mono text-chip text-mute">
              {STATE_LABELS[service.state]}
              {service.url ? ` · ${hostFromUrl(service.url)}` : ""}
            </small>
          </span>
        </span>

        <UptimeBars
          points={summary?.points ?? []}
          label={`${service.name} uptime`}
          decorative
          className="col-span-full row-start-2 md:col-auto md:row-auto"
        />

        <span className="hidden flex-col gap-1 md:flex">
          <span className="font-mono text-[11px] tracking-[0.08em] uppercase text-mute">
            Response
          </span>
          <b className="display text-[18px] tabular-nums normal-case">
            {formatResponseTime(service.responseTimeMs, service.state)}
          </b>
        </span>

        <span className="row-start-3 flex flex-col gap-1 md:row-auto">
          <span className="font-mono text-[11px] tracking-[0.08em] uppercase text-mute">
            Uptime 30d
          </span>
          <b className="display text-[18px] tabular-nums normal-case">
            {formatUptime(service.uptime30d, service.state)}
          </b>
        </span>

        <span
          aria-hidden="true"
          className={cn(
            "col-start-2 row-start-1 flex size-10 items-center justify-center rounded-full",
            "border border-line transition-transform duration-250",
            "group-hover:border-fg md:col-auto md:row-auto",
            expanded && "rotate-180",
          )}
        >
          <ChevronIcon />
        </span>
      </button>

      <div id={panelId} hidden={!expanded}>
        {expanded ? (
          <div className="grid gap-7 border-t border-line p-4.5 md:grid-cols-[minmax(0,3fr)_minmax(0,1fr)] md:p-6">
            <div className="min-w-0">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <h3 className="display m-0 text-[16px] font-extrabold">
                  Uptime · last {window}
                </h3>
                <HistoryWindowTabs
                  window={window}
                  onWindowChange={onWindowChange}
                />
              </div>

              <UptimeBars
                points={points}
                size="chart"
                label={`${service.name} uptime over the last ${window}`}
              />

              <div className="mt-2 flex justify-between font-mono text-[11px] text-mute">
                <span>{WINDOW_AXIS_LABELS[window]}</span>
                <span>now</span>
              </div>

              {/* The strip's own story in words, so the chart never depends on
                  telling four colours apart. */}
              <p className="mt-2 font-mono text-[11px] text-mute">
                {describeStates(points)} {points.length === 1 ? "bucket" : "buckets"}
              </p>

              <div className="mt-5 flex justify-between font-mono text-[11px] text-mute">
                <span>Avg response time (ms)</span>
                <span className="tabular-nums">
                  {averageMs === null ? "—" : averageMs.toLocaleString("en-US")}
                </span>
              </div>
              <ResponseSparkline points={points} label={service.name} />
            </div>

            <div className="grid grid-cols-2 gap-2.5 md:flex md:flex-col md:gap-3.5">
              <Figure
                label={`Uptime · ${window}`}
                value={
                  history === null
                    ? "—"
                    : formatUptime(history.uptime, service.state)
                }
              />
              <Figure
                label="Avg response"
                value={
                  averageMs === null
                    ? "—"
                    : `${averageMs.toLocaleString("en-US")} ms`
                }
              />
              {service.url ? (
                <ButtonLink href={service.url} className="col-span-2 md:col-auto">
                  Open {service.name}
                </ButtonLink>
              ) : null}
              {caseStudyHref ? (
                <ButtonLink href={caseStudyHref} className="col-span-2 md:col-auto">
                  Case Study
                </ButtonLink>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>
    </article>
  );
}

/** `.kv` — a bordered figure in the detail panel's side column. */
function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-line bg-bg p-3.5 px-4">
      <span className="font-mono text-[11px] tracking-[0.08em] uppercase text-mute">
        {label}
      </span>
      <b className="display text-[22px] tabular-nums normal-case">{value}</b>
    </div>
  );
}

/**
 * The history for the selected window, fetched once per window and remembered.
 *
 * Nothing is requested until the row is open, and the 30-day history the server
 * already sent is reused rather than re-fetched — so expanding a row costs no
 * request at all until the reader changes the window. A window that fails to
 * load stays cached as `null`, which the panel renders as an empty chart rather
 * than retrying in a loop.
 */
function useWindowHistory(
  slug: string,
  window: HistoryWindow,
  summary: StatusHistory | null,
  expanded: boolean,
): StatusHistory | null {
  const [cache, setCache] = useState<
    Partial<Record<HistoryWindow, StatusHistory | null>>
  >(() => (summary ? { [SUMMARY_WINDOW]: summary } : {}));

  const cached = cache[window];

  useEffect(() => {
    if (!expanded || cached !== undefined) return;

    let active = true;
    fetchStatusHistory(slug, window).then((history) => {
      if (active) setCache((current) => ({ ...current, [window]: history }));
    });
    return () => {
      active = false;
    };
  }, [expanded, cached, slug, window]);

  return cached ?? null;
}
