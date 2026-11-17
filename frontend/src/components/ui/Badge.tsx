import Link from "next/link";

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

/** `.chip`: a hairline pill in 12px JetBrains Mono. */
const badgeClass =
  "inline-flex items-center gap-1.5 rounded-full border border-line " +
  "bg-transparent px-3 py-1.5 font-mono text-chip whitespace-nowrap text-mute";

type DotProps = {
  tone: BadgeTone;
  live: boolean;
};

function Dot({ tone, live }: DotProps) {
  return (
    <i
      aria-hidden="true"
      className={cn(
        "size-2 shrink-0 rounded-full",
        dotTones[tone],
        live && "motion-safe:animate-[dot-pulse_2s_ease-in-out_infinite]",
      )}
    />
  );
}

type BadgeOwnProps = {
  /** Shows the design's 8px status dot before the label. */
  dot?: BadgeTone;
  /** Pulses the dot, for live values. Respects `prefers-reduced-motion`. */
  live?: boolean;
};

type BadgeProps = React.ComponentPropsWithoutRef<"span"> & BadgeOwnProps;

/**
 * `.chip` from the exports, used for tags, counts and status labels.
 */
export function Badge({
  dot,
  live = false,
  className,
  children,
  ...props
}: BadgeProps) {
  return (
    <span className={cn(badgeClass, className)} {...props}>
      {dot ? <Dot tone={dot} live={live} /> : null}
      {children}
    </span>
  );
}

type BadgeLinkProps = React.ComponentPropsWithoutRef<typeof Link> &
  BadgeOwnProps;

/**
 * The same chip as a real link, for the footer's status chip. Keeps
 * Cmd/middle-click working rather than faking navigation on a span.
 */
export function BadgeLink({
  dot,
  live = false,
  className,
  children,
  ...props
}: BadgeLinkProps) {
  return (
    <Link
      className={cn(
        badgeClass,
        "no-underline transition-colors duration-150 hover:border-fg hover:text-fg",
        className,
      )}
      {...props}
    >
      {dot ? <Dot tone={dot} live={live} /> : null}
      {children}
    </Link>
  );
}
