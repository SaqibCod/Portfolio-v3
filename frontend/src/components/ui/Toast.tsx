"use client";

import { useEffect } from "react";

/** How long a confirmation stays up, from the export's 2s timer. */
const TOAST_MS = 2000;

/**
 * `.toast` from the palette export: the inverted pill that confirms an action
 * with no other visible outcome — "Email copied", "Opening /projects".
 *
 * A `role="status"` region, so the confirmation is announced once rather than
 * only drawn. It is deliberately the *only* feedback these actions get: a
 * copied address and a started navigation are both invisible otherwise.
 */
type ToastProps = {
  /** The message, or null for nothing on screen. Changing it restarts the timer. */
  message: string | null;
  /** Called when the message has been up long enough. */
  onExpire: () => void;
};

export function Toast({ message, onExpire }: ToastProps) {
  useEffect(() => {
    if (!message) return;
    const timer = window.setTimeout(onExpire, TOAST_MS);
    return () => window.clearTimeout(timer);
  }, [message, onExpire]);

  return (
    // Always rendered, empty when idle: a live region has to be in the document
    // before the text arrives, or a screen reader has nothing to announce.
    <div
      role="status"
      className="pointer-events-none fixed bottom-7 left-1/2 z-50 -translate-x-1/2"
    >
      {message ? (
        // `max-w` and `truncate` rather than the export's bare `nowrap`: a
        // clipboard failure puts the address itself in the message, and that
        // can be longer than a phone is wide.
        <div className="flex max-w-[min(90vw,26rem)] items-center gap-2.5 rounded-full bg-fg px-4.5 py-3 text-[14px] font-semibold text-bg shadow-toast">
          <span
            aria-hidden="true"
            className="flex size-5 shrink-0 items-center justify-center rounded-full bg-ok text-white"
          >
            <svg
              width="11"
              height="11"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="4"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M5 12l5 5 9-10" />
            </svg>
          </span>
          <span className="min-w-0 truncate">{message}</span>
        </div>
      ) : null}
    </div>
  );
}
