"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { usePresenceCursors } from "@/components/presence/usePresence";
import { CURSOR_IDLE_MS, type CursorBroadcastMessage } from "@/lib/presence";

/** The avatar palette from `globals.css`, reused so cursors sit in the system. */
const CURSOR_COLORS = [
  "var(--av-1)",
  "var(--av-2)",
  "var(--av-3)",
  "var(--av-4)",
  "var(--av-5)",
  "var(--av-6)",
] as const;

/** A stable colour per connection id, so one person keeps one colour. */
function colorFor(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) | 0;
  }
  return CURSOR_COLORS[Math.abs(hash) % CURSOR_COLORS.length];
}

/**
 * Whether this visitor should see or send cursors at all.
 *
 * Two hard exclusions, both from the phase file. A coarse pointer has no hover
 * position to send — a touch is a tap, not a cursor — and a visitor who has
 * asked for reduced motion has asked not to be shown several things gliding
 * around the page they are trying to read. Neither is a preference to override
 * with a toggle: the toggle below only turns cursors off for people who could
 * have had them.
 */
function useCursorsSupported(): boolean {
  const [supported, setSupported] = useState(false);

  useEffect(() => {
    const fine = window.matchMedia("(pointer: fine)");
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");

    const update = () => setSupported(fine.matches && !reduced.matches);
    update();

    fine.addEventListener("change", update);
    reduced.addEventListener("change", update);
    return () => {
      fine.removeEventListener("change", update);
      reduced.removeEventListener("change", update);
    };
  }, []);

  return supported;
}

type RemoteCursor = CursorBroadcastMessage & { seenAt: number };

/**
 * Other visitors' cursors, drawn over the page.
 *
 * `aria-hidden`, and pointer-events-none throughout: this is ambient
 * decoration that duplicates nothing, so it is kept out of the accessibility
 * tree entirely rather than described to people who cannot see it.
 *
 * Sends are throttled to one per animation frame, which is what keeps a
 * 1000 Hz gaming mouse inside the server's twenty-per-second budget without
 * the client having to count anything. Receives never touch React state on the
 * socket's schedule — the cursors live in a ref and are flushed to state on a
 * frame — so another person moving their mouse cannot re-render the page.
 */
export function LiveCursors() {
  const supported = useCursorsSupported();
  const { sendCursor, subscribeToCursors, connected } = usePresenceCursors();

  /**
   * Off is remembered for the session only, not persisted. A visitor turning
   * cursors off is reacting to this visit — usually "I am trying to read" —
   * and a choice made once should not silently disable a feature on a return
   * visit months later. `sessionStorage` would survive a reload within the tab;
   * component state is deliberately narrower than that.
   */
  const [enabled, setEnabled] = useState(true);

  const cursorsRef = useRef<Map<string, RemoteCursor>>(new Map());
  const [cursors, setCursors] = useState<RemoteCursor[]>([]);
  const frameRef = useRef<number | null>(null);
  const dirtyRef = useRef(false);

  const active = supported && enabled && connected;

  /** Copies the ref into state, dropping anything that has gone idle. */
  const flush = useCallback(() => {
    frameRef.current = null;
    const cutoff = performance.now() - CURSOR_IDLE_MS;
    let changed = dirtyRef.current;
    dirtyRef.current = false;

    for (const [id, cursor] of cursorsRef.current) {
      if (cursor.seenAt < cutoff) {
        cursorsRef.current.delete(id);
        changed = true;
      }
    }
    if (changed) setCursors([...cursorsRef.current.values()]);
  }, []);

  const scheduleFlush = useCallback(() => {
    if (frameRef.current !== null) return;
    frameRef.current = requestAnimationFrame(flush);
  }, [flush]);

  // Receive.
  useEffect(() => {
    // Starting or stopping both begin from nothing: a cursor seen before the
    // layer went quiet must not reappear where its owner left it. The state is
    // rewritten from the emptied ref on the next frame rather than here, both
    // because a synchronous setState in an effect body is a cascading render
    // and because one flush path is easier to reason about than two.
    cursorsRef.current.clear();
    dirtyRef.current = true;
    scheduleFlush();

    if (!active) return;

    const unsubscribe = subscribeToCursors((cursor) => {
      cursorsRef.current.set(cursor.id, {
        ...cursor,
        seenAt: performance.now(),
      });
      dirtyRef.current = true;
      scheduleFlush();
    });

    // Sweeps cursors whose owner stopped moving, which no incoming message
    // would otherwise prompt. One second is far finer than the five-second
    // idle it is enforcing.
    const sweep = window.setInterval(scheduleFlush, 1_000);

    return () => {
      unsubscribe();
      window.clearInterval(sweep);
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    };
  }, [active, scheduleFlush, subscribeToCursors]);

  // Send.
  useEffect(() => {
    if (!active) return;

    let pending: { x: number; y: number } | null = null;
    let frame: number | null = null;

    const onPointerMove = (event: PointerEvent) => {
      // Normalised against the viewport, so a position means the same thing on
      // a laptop and on an ultrawide. Clamped because a drag can report a
      // coordinate just outside the window, and the server drops anything
      // outside 0..1 rather than relaying it.
      pending = {
        x: Math.min(Math.max(event.clientX / window.innerWidth, 0), 1),
        y: Math.min(Math.max(event.clientY / window.innerHeight, 0), 1),
      };
      if (frame !== null) return;
      frame = requestAnimationFrame(() => {
        frame = null;
        if (pending) sendCursor(pending.x, pending.y);
        pending = null;
      });
    };

    window.addEventListener("pointermove", onPointerMove, { passive: true });
    return () => {
      window.removeEventListener("pointermove", onPointerMove);
      if (frame !== null) cancelAnimationFrame(frame);
    };
  }, [active, sendCursor]);

  if (!supported) return null;

  return (
    <>
      {active && cursors.length > 0 ? (
        <div
          aria-hidden="true"
          className="pointer-events-none fixed inset-0 z-50 overflow-hidden"
        >
          {cursors.map((cursor) => (
            <svg
              key={cursor.id}
              width="18"
              height="18"
              viewBox="0 0 18 18"
              className="absolute"
              style={{
                left: `${cursor.x * 100}%`,
                top: `${cursor.y * 100}%`,
                color: colorFor(cursor.id),
              }}
            >
              {/* A plain arrow, filled in the visitor's colour with a
                  contrasting edge so it stays visible on both themes. */}
              <path
                d="M2 1l11 7-5 1.2L5.4 15z"
                fill="currentColor"
                stroke="var(--bg)"
                strokeWidth="1.2"
              />
            </svg>
          ))}
        </div>
      ) : null}

      {/* Only offered when there is something to turn off, or something to
          turn back on. A permanent control for a feature that is idle whenever
          nobody else is reading would be clutter the exports do not have — and
          it sits bottom-left, clear of the chat launcher's bottom-right. */}
      {connected && (cursors.length > 0 || !enabled) ? (
        <button
          type="button"
          onClick={() => setEnabled((on) => !on)}
          aria-pressed={!enabled}
          className="fixed bottom-[calc(1rem+env(safe-area-inset-bottom))] left-4 z-30 cursor-pointer rounded-full border border-line bg-nav px-3 py-1.5 font-mono text-chip text-mute backdrop-blur-[14px] transition-colors duration-150 hover:border-fg hover:text-fg md:bottom-[calc(1.5rem+env(safe-area-inset-bottom))] md:left-6"
        >
          {enabled ? "Hide cursors" : "Show cursors"}
        </button>
      ) : null}
    </>
  );
}
