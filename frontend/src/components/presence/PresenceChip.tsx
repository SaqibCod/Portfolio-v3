import { Badge } from "@/components/ui/Badge";

type PresenceChipProps = {
  /**
   * Live viewer count. Phase 09 supplies this from the presence WebSocket;
   * until then it is always undefined and the chip renders nothing rather
   * than showing a number nobody measured.
   */
  count?: number;
  /**
   * `short` is the header's "11 here now"; `long` is the footer's
   * "11 people here now".
   */
  wording?: "short" | "long";
  /** The header dot pulses; the footer's is static, as in the exports. */
  live?: boolean;
  className?: string;
};

/** Fixed locale so the server and client render the same string. */
const formatCount = (count: number) => count.toLocaleString("en-US");

function label(count: number, wording: "short" | "long"): string {
  const formatted = formatCount(count);
  if (wording === "short") return `${formatted} here now`;
  return `${formatted} ${count === 1 ? "person" : "people"} here now`;
}

/**
 * The live viewer count chip from the exports' header and footer.
 *
 * Phase 02 ships the presentation only. There is deliberately no `aria-live`:
 * a counter that re-announces on every join and leave is hostile to screen
 * reader users, so phase 09 should decide that alongside the real update
 * cadence.
 */
export function PresenceChip({
  count,
  wording = "short",
  live = true,
  className,
}: PresenceChipProps) {
  if (count === undefined || count < 0) return null;

  return (
    <Badge dot="ok" live={live} className={className}>
      {label(count, wording)}
    </Badge>
  );
}
