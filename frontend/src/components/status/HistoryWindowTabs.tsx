"use client";

import { cn } from "@/lib/cn";
import { HISTORY_WINDOWS, type HistoryWindow } from "@/lib/status";

type HistoryWindowTabsProps = {
  window: HistoryWindow;
  onWindowChange: (window: HistoryWindow) => void;
  className?: string;
};

/**
 * `.tabs` from the status export: the 24h / 7d / 30d switch above a chart.
 *
 * Controlled rather than stateful, because the design shows one window across
 * every expanded row at once — a reader comparing two projects should not have
 * to set the window twice. `StatusOverview` owns the value, mirrors it into the
 * URL, and fetches what the choice needs.
 *
 * A group of toggle buttons rather than an ARIA tablist. There is no panel to
 * own and no arrow-key model to get wrong: each button replaces the chart
 * beside it, which is exactly what `aria-pressed` describes.
 */
export function HistoryWindowTabs({
  window,
  onWindowChange,
  className,
}: HistoryWindowTabsProps) {
  return (
    <div
      role="group"
      aria-label="Time window"
      className={cn(
        "inline-flex gap-0.5 rounded-full border border-line p-[3px]",
        className,
      )}
    >
      {HISTORY_WINDOWS.map((candidate) => {
        const selected = candidate === window;
        return (
          <button
            key={candidate}
            type="button"
            aria-pressed={selected}
            onClick={() => onWindowChange(candidate)}
            className={cn(
              "min-h-9 cursor-pointer touch-manipulation rounded-full px-3.5",
              "font-mono text-chip font-semibold leading-none",
              "transition-colors duration-150",
              selected
                ? "bg-fg text-bg"
                : "bg-transparent text-mute hover:text-fg",
            )}
          >
            {candidate}
          </button>
        );
      })}
    </div>
  );
}
