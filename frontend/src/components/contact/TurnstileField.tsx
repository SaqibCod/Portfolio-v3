"use client";

import { useMemo, useState, useSyncExternalStore } from "react";
import { Turnstile } from "@marsidev/react-turnstile";
import type { TurnstileInstance } from "@marsidev/react-turnstile";
import { useTheme } from "next-themes";

import { cn } from "@/lib/cn";
import { env } from "@/lib/env";

export type TurnstileFieldProps = {
  /** Set once the widget has produced a token. */
  onToken: (token: string) => void;
  /** Called when the token is gone — expired, errored, or reset after a send. */
  onClear: () => void;
  /** The ref the form uses to reset the widget; tokens are single-use. */
  widgetRef: React.RefObject<TurnstileInstance | null>;
  /** Shown instead of the default caption when the check itself failed. */
  invalid?: boolean;
  /**
   * Which form this widget belongs to. Cloudflare reports solve and failure
   * rates per action, so the two forms have to be distinguishable there.
   */
  action?: string;
  /**
   * Set when the field sits on a band whose ground is the opposite of the
   * page's, which is where the home page's contact form lives. The widget's
   * own theme is then flipped to match what is actually behind it.
   */
  invertedGround?: boolean;
};

type Status = "loading" | "waiting" | "solved" | "expired" | "error";

/** Mount is a one-way change, so there is nothing to subscribe to. */
const noopSubscribe = () => () => {};

const STATUS_TEXT: Record<Status, string> = {
  loading: "Loading the bot check…",
  waiting: "Checking you’re human…",
  solved: "Verified",
  expired: "The check expired — redoing it…",
  error: "The check couldn’t load. Reload the page and try again.",
};

/**
 * `.ts` from the exports: the hairline-framed row that holds the bot check.
 *
 * The export draws a mock "Verified" checkbox here, captioned
 * `[Cloudflare Turnstile]` — bracketed the same way as every other stand-in in
 * these files, so it marks where the real widget belongs rather than being
 * final copy. The frame, radius and caption are kept; the widget inside is the
 * real one.
 *
 * Mounting is deferred by one effect so the widget is rendered once with a
 * settled theme. `next-themes` cannot know the resolved theme until it is on
 * the client, and re-rendering the widget to correct the theme afterwards
 * would discard the token it had already produced.
 */
export function TurnstileField({
  onToken,
  onClear,
  widgetRef,
  invalid = false,
  action = "contact",
  invertedGround = false,
}: TurnstileFieldProps) {
  const [status, setStatus] = useState<Status>("loading");
  const { resolvedTheme } = useTheme();

  // False while rendering on the server and during hydration, then true — the
  // same hydration-safe "am I mounted" the theme toggle uses.
  const mounted = useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );

  // On an inverted band the widget's ground is the opposite of the page's, so
  // its theme has to be flipped to match what is behind it; anywhere else it
  // follows the page.
  const options = useMemo(() => {
    const pageIsDark = resolvedTheme === "dark";
    const widgetIsDark = invertedGround ? !pageIsDark : pageIsDark;
    return {
      theme: widgetIsDark ? ("dark" as const) : ("light" as const),
      action,
    };
  }, [resolvedTheme, invertedGround, action]);

  const shown: Status = invalid && status === "solved" ? "error" : status;

  return (
    <div
      className={cn(
        "flex flex-col gap-3 rounded-input border bg-bg2 p-3",
        "sm:flex-row sm:items-center sm:justify-between sm:gap-3 sm:px-4",
        invalid || status === "error" ? "border-err" : "border-line",
      )}
    >
      {mounted ? (
        <Turnstile
          ref={widgetRef}
          siteKey={env.turnstileSiteKey}
          options={options}
          onWidgetLoad={() => setStatus("waiting")}
          onSuccess={(token) => {
            setStatus("solved");
            onToken(token);
          }}
          onExpire={() => {
            setStatus("expired");
            onClear();
          }}
          onError={() => {
            setStatus("error");
            onClear();
          }}
        />
      ) : (
        // Reserves the widget's height so nothing jumps when it arrives.
        <div className="h-[65px] w-[300px] max-w-full" aria-hidden="true" />
      )}

      <p
        className="font-mono text-[11px] text-mute"
        // The status changes on its own, without any action from the visitor,
        // so it has to be announced rather than only shown.
        aria-live="polite"
      >
        {STATUS_TEXT[shown]}
      </p>
    </div>
  );
}
