import { cn } from "@/lib/cn";

export type BadgeTone = "default" | "ok" | "warn" | "err" | "off";

/**
 * Each tone also publishes `--dot-color`, which the `dot-pulse` keyframe mixes
 * down to build the ring, so a live dot always pulses its own colour.
 */
const dotTones: Record<BadgeTone, string> = {
  default: "bg-mute [--dot-color:var(--mute)]",
  ok: "bg-ok [--dot-color:var(--ok)]",
  warn: "bg-warn [--dot-color:var(--warn)]",
  err: "bg-err [--dot-color:var(--err)]",
  off: "bg-off [--dot-color:var(--off)]",
};

type BadgeProps = React.ComponentPropsWithoutRef<"span"> & {
  /** Shows the design's 8px status dot before the label. */
  dot?: BadgeTone;
  /** Pulses the dot, for live values. Respects `prefers-reduced-motion`. */
  live?: boolean;
};

/**
 * `.chip` from the exports: a hairline pill in 12px JetBrains Mono, used for
 * tags, counts and status labels.
 */
export function Badge({ dot, live = false, className, children, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border border-line " +
          "bg-transparent px-3 py-1.5 font-mono text-chip whitespace-nowrap text-mute",
        className,
      )}
      {...props}
    >
      {dot ? (
        <i
          aria-hidden="true"
          className={cn(
            "size-2 shrink-0 rounded-full",
            dotTones[dot],
            live &&
              "motion-safe:animate-[dot-pulse_2s_ease-in-out_infinite]",
          )}
        />
      ) : null}
      {children}
    </span>
  );
}
