"use client";

import { useTheme } from "next-themes";
import { useSyncExternalStore } from "react";

import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

function SunIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
    </svg>
  );
}

/** The resolved theme never changes identity, so nothing to subscribe to. */
const noopSubscribe = () => () => {};

type ThemeToggleProps = {
  /** `icon` is the circular nav control; `chip` is the footer text button. */
  variant?: "icon" | "chip";
  className?: string;
};

/**
 * Switches between the two themes. The accessible label names the theme the
 * press will move to, so it reflects the current state.
 *
 * The resolved theme is unknown during server rendering, so the control renders
 * at its final size but stays unlabelled until mount — that avoids both a
 * hydration mismatch and a layout shift in the sticky header.
 */
export function ThemeToggle({ variant = "icon", className }: ThemeToggleProps) {
  const { resolvedTheme, setTheme } = useTheme();

  // The active theme only exists on the client. This reads false while
  // rendering on the server and during hydration, then true — a hydration-safe
  // "am I mounted" without a setState-in-effect.
  const mounted = useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );

  const isDark = resolvedTheme === "dark";
  const next = isDark ? "light" : "dark";
  const label = mounted
    ? isDark
      ? "Switch to light theme"
      : "Switch to dark theme"
    : "Switch theme";

  if (variant === "chip") {
    return (
      <button
        type="button"
        onClick={() => setTheme(next)}
        aria-label={label}
        className={cn(
          "inline-flex min-h-8 cursor-pointer touch-manipulation items-center gap-1.5 " +
            "rounded-full border border-line bg-transparent px-3 py-1.5 " +
            "font-mono text-chip whitespace-nowrap text-mute " +
            "transition-colors duration-150 hover:border-fg hover:text-fg",
          className,
        )}
      >
        {mounted ? (isDark ? "Switch to light" : "Switch to dark") : "Theme"}
      </button>
    );
  }

  return (
    <Button
      size="icon"
      onClick={() => setTheme(next)}
      aria-label={label}
      className={className}
    >
      {!mounted ? null : isDark ? <SunIcon /> : <MoonIcon />}
    </Button>
  );
}
