"use client";

import { PresenceChip } from "@/components/presence/PresenceChip";
import { usePresence } from "@/components/presence/usePresence";

type PresenceBadgeProps = {
  /** `short` is the header's "11 here now"; `long` the footer's longer form. */
  wording?: "short" | "long";
  /** The header's dot pulses; the footer's is static, as in the exports. */
  live?: boolean;
  className?: string;
};

/**
 * The live viewer count, wired to the presence socket.
 *
 * This is the component the phase-09 LLD calls `PresenceBadge`, and it holds
 * only the two rules about *whether* to show a count. The count itself is
 * rendered by {@link PresenceChip}, which is the design's chip from phase 02 —
 * so the wording here stays the export's "11 here now" rather than the LLD's
 * "3 viewing now", because the exports are the source of truth for UI copy.
 *
 * Hidden unless connected and unless at least two people are here:
 *
 *   - Disconnected means the number is unknown, not zero. Showing the last
 *     count after the socket dropped would be stating something false, and
 *     showing "0 here now" to someone who is demonstrably here would be worse.
 *   - "1 here now" is a true statement that reads as an accidental admission
 *     and tells the visitor nothing — they know they are here. The count is
 *     only interesting once it is about other people.
 *
 * There is deliberately no `aria-live`. A counter that re-announces every join
 * and leave is hostile to screen reader users, and the number repeats nothing
 * that is not already decoration, so it is left out of the announcement queue
 * rather than throttled into one.
 */
export function PresenceBadge({
  wording = "short",
  live = true,
  className,
}: PresenceBadgeProps) {
  const { pageCount, connected } = usePresence();

  if (!connected || pageCount < 2) return null;

  return (
    <PresenceChip
      count={pageCount}
      wording={wording}
      live={live}
      className={className}
    />
  );
}
