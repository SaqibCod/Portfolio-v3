"use client";

import { usePathname } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";

import {
  HIDDEN_DISCONNECT_MS,
  PING_INTERVAL_MS,
  parseInboundMessage,
  presenceUrl,
  reconnectDelayMs,
  type OutboundMessage,
  type PresenceMessage,
} from "@/lib/presence";

import { PresenceContext } from "./usePresence";

/**
 * Waits for the browser to be idle, falling back to a short timeout where
 * `requestIdleCallback` does not exist (Safari, at the time of writing).
 * Returns its own cancel function.
 */
function whenIdle(run: () => void): () => void {
  if (typeof window.requestIdleCallback === "function") {
    const handle = window.requestIdleCallback(run, { timeout: 3_000 });
    return () => window.cancelIdleCallback(handle);
  }
  const handle = window.setTimeout(run, 1_500);
  return () => window.clearTimeout(handle);
}

/**
 * Holds the one presence socket for the whole site and publishes what arrives
 * on it.
 *
 * Mounted in the root layout, so it survives navigation: a route change sends
 * a `join` on the socket that is already open rather than opening a new one,
 * which is both cheaper and what lets the server tell the page being left that
 * its count has dropped.
 *
 * Nothing here is allowed to be visible when it goes wrong. The backend being
 * down, a socket failing to open, a proxy killing an idle connection — all of
 * them end in the same place, a `connected: false` that hides the badge and
 * the badge, and a reconnect attempt on a jittered backoff. There is no error
 * state to render because there is nothing a visitor could do about it and
 * nothing they are missing: every number this provides exists only to decorate
 * content that is already in the HTML.
 */
export function PresenceProvider({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [connected, setConnected] = useState(false);
  /**
   * The last counts the server sent, whichever page they were about.
   *
   * Kept as the message rather than as bare numbers so that which page they
   * describe travels with them. Whether they apply is then decided during
   * render, by comparing against the current pathname — which is both simpler
   * than clearing them on navigation and more correct: the server can answer a
   * join before React has run the effect for that navigation, and a derived
   * value cannot be caught out by that ordering.
   */
  const [counts, setCounts] = useState<PresenceMessage | null>(null);

  /**
   * The page to announce. Read by the socket when it opens, which can happen
   * after several navigations, so it has to be the current value rather than
   * the one captured when the connection was first attempted.
   */
  const pageRef = useRef(pathname);
  /** Set by the lifecycle effect so the navigation effect can send a join. */
  const joinRef = useRef<((page: string) => void) | null>(null);

  // One effect owns the whole connection lifecycle. Splitting it across several
  // would mean the reconnect timer, the heartbeat and the hidden-tab timer each
  // had to reason about state the others own; here they close over the same
  // handful of locals and are torn down together.
  useEffect(() => {
    let socket: WebSocket | null = null;
    let attempt = 0;
    let reconnectTimer: number | undefined;
    let pingTimer: number | undefined;
    let hiddenTimer: number | undefined;
    // Distinguishes "we closed this" from "it dropped". Only the second
    // reconnects; without it, pausing a hidden tab would immediately undo
    // itself.
    let closedDeliberately = false;
    let disposed = false;

    const send = (message: OutboundMessage) => {
      if (socket?.readyState !== WebSocket.OPEN) return;
      socket.send(JSON.stringify(message));
    };

    const stopPing = () => {
      window.clearInterval(pingTimer);
      pingTimer = undefined;
    };

    const startPing = () => {
      stopPing();
      pingTimer = window.setInterval(
        () => send({ type: "ping" }),
        PING_INTERVAL_MS,
      );
    };

    const scheduleReconnect = () => {
      if (disposed || reconnectTimer !== undefined) return;
      const delay = reconnectDelayMs(attempt);
      attempt += 1;
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = undefined;
        connect();
      }, delay);
    };

    function connect() {
      if (disposed || socket) return;

      closedDeliberately = false;
      let opening: WebSocket;
      try {
        opening = new WebSocket(presenceUrl());
      } catch {
        // A malformed URL or a blocked scheme. Retrying cannot fix either, but
        // the backoff means trying anyway costs nothing worth measuring, and
        // the alternative is a feature that stays dead until a reload.
        scheduleReconnect();
        return;
      }
      socket = opening;

      opening.addEventListener("open", () => {
        if (disposed) return;
        attempt = 0;
        setConnected(true);
        send({ type: "join", page: pageRef.current });
        startPing();
      });

      opening.addEventListener("message", (event: MessageEvent) => {
        // Counts are the only thing the server sends; anything else a future
        // release adds is discarded here rather than throwing.
        const message = parseInboundMessage(event.data);
        if (!message) return;

        // Stored whichever page it is about; render decides whether it is
        // about the page being looked at now.
        setCounts(message);
      });

      opening.addEventListener("close", () => {
        stopPing();
        socket = null;
        if (disposed) return;
        // The counts were only true while the socket was open.
        setConnected(false);
        setCounts(null);
        if (!closedDeliberately) scheduleReconnect();
      });

      // `error` is always followed by `close`, which does the reconnecting.
      // Listening at all is what stops the browser reporting it as unhandled.
      opening.addEventListener("error", () => {});
    }

    const disconnect = () => {
      closedDeliberately = true;
      stopPing();
      window.clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
      socket?.close();
      socket = null;
      setConnected(false);
      setCounts(null);
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === "hidden") {
        // Five minutes, not immediately: a tab switched away from for a moment
        // is still a person reading the site, and dropping the socket at once
        // would make every alt-tab a disconnect and a reconnect.
        hiddenTimer = window.setTimeout(disconnect, HIDDEN_DISCONNECT_MS);
        return;
      }
      window.clearTimeout(hiddenTimer);
      hiddenTimer = undefined;
      if (!socket) {
        attempt = 0;
        connect();
      }
    };

    joinRef.current = (page: string) => send({ type: "join", page });

    // Connecting is deferred to the first idle moment. It is a socket for a
    // decoration, and it must not compete with hydration, fonts or the first
    // real paint for the main thread.
    const cancelIdle = whenIdle(connect);

    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      disposed = true;
      joinRef.current = null;
      cancelIdle();
      document.removeEventListener("visibilitychange", onVisibilityChange);
      window.clearTimeout(hiddenTimer);
      window.clearTimeout(reconnectTimer);
      stopPing();
      socket?.close();
      socket = null;
    };
  }, []);

  // Every route change announces the new page on the existing socket.
  useEffect(() => {
    pageRef.current = pathname;
    joinRef.current?.(pathname);
  }, [pathname]);

  // Counts for some other page are not this page's counts, and a disconnected
  // socket has none at all. Both cases read as zero, which the badge hides.
  const current = connected && counts?.page === pathname ? counts : null;

  const value = useMemo(
    () => ({
      pageCount: current?.pageCount ?? 0,
      siteCount: current?.siteCount ?? 0,
      connected,
    }),
    [current, connected],
  );

  return (
    <PresenceContext.Provider value={value}>
      {children}
    </PresenceContext.Provider>
  );
}
