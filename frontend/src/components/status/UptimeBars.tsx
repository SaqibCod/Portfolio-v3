import { cn } from "@/lib/cn";
import {
  describeStates,
  STATE_LABELS,
  type ServiceState,
  type StatusPoint,
} from "@/lib/status";

/**
 * `.strip i` / `.ub i` from the status export. `unknown` is both a different
 * colour and a lower opacity, so a stretch nobody probed reads as absent
 * rather than as a fourth kind of result.
 */
const barTones: Record<ServiceState, string> = {
  operational: "bg-ok opacity-85",
  degraded: "bg-warn opacity-85",
  down: "bg-err opacity-85",
  unknown: "bg-off opacity-45",
};

/**
 * `.strip` is the 32px summary inside a row; `.ub` is the 56px chart in the
 * expanded detail, where buckets are narrower and packed tighter.
 */
const sizes = {
  strip: "h-8 gap-[3px] [&>i]:rounded-[3px]",
  chart: "h-14 gap-px [&>i]:rounded-[2px]",
} as const;

type UptimeBarsProps = {
  points: StatusPoint[];
  size?: keyof typeof sizes;
  /** Names the service and window in the chart's text alternative. */
  label: string;
  /**
   * Hides the strip from assistive technology entirely, for the copy inside a
   * row's disclosure button. Without it the strip's label would be folded into
   * that button's accessible name, which already states the service, its state
   * and its uptime — the figure the strip illustrates.
   */
  decorative?: boolean;
  className?: string;
};

/**
 * One bar per history bucket.
 *
 * The accessibility problem here is the same one the contribution graph has:
 * 288 bars are 288 meaningless stops for a screen reader. So the strip is a
 * single `role="img"` whose label states the window's shape in words — "27
 * operational · 2 degraded · 1 down" — and the bars inside it are hidden. The
 * per-bucket detail stays available to a mouse through each bar's `title`.
 *
 * That summary is also printed as visible text by the caller, which is what
 * makes the chart readable without seeing colour at all.
 */
export function UptimeBars({
  points,
  size = "strip",
  label,
  decorative = false,
  className,
}: UptimeBarsProps) {
  const described = decorative
    ? { "aria-hidden": true as const }
    : { role: "img", "aria-label": `${label}: ${describeStates(points)}` };

  if (points.length === 0) {
    return (
      <span
        className={cn("flex items-center", sizes[size], className)}
        {...(decorative
          ? { "aria-hidden": true as const }
          : { role: "img", "aria-label": `${label}: no data` })}
      >
        <i aria-hidden="true" className="flex-1 rounded-[3px] bg-off opacity-45" />
      </span>
    );
  }

  return (
    <span className={cn("flex", sizes[size], className)} {...described}>
      {points.map((point) => (
        <i
          key={point.timestamp}
          aria-hidden="true"
          title={barTitle(point)}
          className={cn("min-w-px flex-1", barTones[point.state])}
        />
      ))}
    </span>
  );
}

/**
 * The tooltip on one bucket: when it was, how it went, and how fast.
 *
 * `toISOString` rather than a localised format. These strings are rendered on
 * the server for the row strips, and a locale- or zone-dependent label would be
 * computed there and then disagree with the browser — the classic hydration
 * mismatch. The axis labels around the chart carry the human-readable range.
 */
function barTitle(point: StatusPoint): string {
  const when = point.timestamp.replace(".000Z", "Z");
  const state = STATE_LABELS[point.state];
  const speed =
    point.avgResponseTimeMs === null || point.avgResponseTimeMs === undefined
      ? ""
      : ` · ${point.avgResponseTimeMs} ms`;

  return `${when} · ${state}${speed}`;
}
