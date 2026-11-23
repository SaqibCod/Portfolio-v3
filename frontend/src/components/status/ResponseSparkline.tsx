import {
  averageResponseTime,
  SPARKLINE_VIEWBOX,
  sparklinePoints,
  type StatusPoint,
} from "@/lib/status";

type ResponseSparklineProps = {
  points: StatusPoint[];
  /** Names the service in the chart's text alternative. */
  label: string;
};

/**
 * `.spark` from the status export: response time across the window, as a line
 * on a dashed baseline.
 *
 * Decorative in the strict sense — the figure beside it ("Avg response time")
 * carries the number, and the bar strip above carries the uptime story — so its
 * label describes the trend rather than trying to enumerate 288 values. It
 * renders nothing at all when fewer than two buckets measured anything, since a
 * single point is not a trend and an empty box is noise.
 */
export function ResponseSparkline({ points, label }: ResponseSparklineProps) {
  const polyline = sparklinePoints(points);
  if (!polyline) return null;

  const average = averageResponseTime(points);

  return (
    <svg
      viewBox={`0 0 ${SPARKLINE_VIEWBOX.width} ${SPARKLINE_VIEWBOX.height}`}
      preserveAspectRatio="none"
      role="img"
      aria-label={
        average === null
          ? `${label} response time trend`
          : `${label} response time trend, averaging ${average} ms`
      }
      className="mt-2 block h-24 w-full border-b border-dashed border-line"
    >
      <polyline
        points={polyline}
        fill="none"
        stroke="var(--ac1)"
        strokeWidth="2.5"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
