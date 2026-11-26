"use client";

import { useSyncExternalStore } from "react";

import { requestOpenPalette } from "@/components/palette/open-palette";
import { Button } from "@/components/ui/Button";
import { Kbd } from "@/components/ui/Kbd";
import { cn } from "@/lib/cn";

/**
 * `.kbtn` and `.sbtn` from the export: the header's way into the palette.
 *
 * Two controls, one job. The pill names the shortcut, because a shortcut nobody
 * is told about does not exist; below 760px it becomes a search icon, since
 * there is no room for the pill and no keyboard to press anyway. Both are
 * buttons rather than a link — the palette is not a route.
 */

/** Nothing to subscribe to: the keyboard does not change under the visitor. */
const noopSubscribe = () => () => {};

function isApplePlatform(): boolean {
  // `userAgentData.platform` where it exists, `platform` elsewhere. Both are
  // only read after mount, so neither can affect the server-rendered markup.
  const data = (
    navigator as Navigator & { userAgentData?: { platform?: string } }
  ).userAgentData;

  return /mac|iphone|ipad|ipod/i.test(data?.platform ?? navigator.platform);
}

/**
 * Which modifier this visitor's keyboard uses.
 *
 * Server-rendered as the design's label and corrected on mount, which is why
 * the element suppresses its hydration warning: the alternative is an empty
 * cap that pops in, and the pill would change width as it did.
 */
function useShortcutLabel(): string {
  return useSyncExternalStore(
    noopSubscribe,
    () => (isApplePlatform() ? "⌘K" : "Ctrl K"),
    () => "⌘K",
  );
}

function SearchIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </svg>
  );
}

type PaletteHintProps = {
  className?: string;
};

export function PaletteHint({ className }: PaletteHintProps) {
  const shortcut = useShortcutLabel();

  return (
    <>
      <button
        type="button"
        onClick={requestOpenPalette}
        aria-label="Open command palette"
        aria-keyshortcuts="Meta+K Control+K"
        className={cn(
          "hidden min-h-11 cursor-pointer items-center gap-2.5 rounded-full " +
            "border border-line bg-bg2 pr-2 pl-4 text-btn font-medium text-mute " +
            "transition-colors duration-150 hover:border-fg hover:text-fg " +
            "md:inline-flex",
          className,
        )}
      >
        Search or jump to&hellip;
        <Kbd suppressHydrationWarning>{shortcut}</Kbd>
      </button>

      <Button
        size="icon"
        onClick={requestOpenPalette}
        aria-label="Open command palette"
        className="md:hidden"
      >
        <SearchIcon />
      </Button>
    </>
  );
}
