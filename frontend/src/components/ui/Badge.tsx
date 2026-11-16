import { cn } from "@/lib/cn";

export type BadgeTone = "default" | "ok" | "warn" | "err" | "off";

const dotTones: Record<BadgeTone, string> = {
  default: "bg-mute",
  ok: "bg-ok",
  warn: "bg-warn",
  err: "bg-err",
  off: "bg-off",
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
            live && "motion-safe:animate-pulse",
          )}
        />
      ) : null}
      {children}
    </span>
  );
}
