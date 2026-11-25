"use client";

import { createContext, useContext } from "react";

import type { CursorBroadcastMessage } from "@/lib/presence";

/** What the badge reads. */
export type PresenceState = {
  /** Visitors viewing this page, including this one. 0 while disconnected. */
  pageCount: number;
  /** Visitors anywhere on the site. 0 while disconnected. */
  siteCount: number;
  /** Whether the socket is open. Counts mean nothing when this is false. */
  connected: boolean;
};

export type CursorListener = (cursor: CursorBroadcastMessage) => void;

export type PresenceContextValue = PresenceState & {
  /**
   * Sends this visitor's cursor, normalised to the viewport. A no-op while
   * disconnected, so callers never have to check first.
   */
  sendCursor: (x: number, y: number) => void;
  /**
   * Subscribes to other visitors' cursors. Returns an unsubscribe function.
   *
   * Cursors arrive up to twenty times a second per visitor and are delivered
   * through a subscription rather than through state on purpose: putting them
   * in React state would re-render the whole subtree under the provider --
   * which is the entire site -- on every frame of every other person's mouse
   * movement. The subscriber keeps them in a ref and draws them itself.
   */
  subscribeToCursors: (listener: CursorListener) => () => void;
};

/**
 * Disconnected, and inert. This is what the hooks return when there is no
 * provider above them, which is the right answer rather than a thrown error:
 * presence is a decoration, and a component that renders it must not break a
 * page -- or a test -- that has not opted into a socket.
 */
export const presenceFallback: PresenceContextValue = {
  pageCount: 0,
  siteCount: 0,
  connected: false,
  sendCursor: () => {},
  subscribeToCursors: () => () => {},
};

export const PresenceContext =
  createContext<PresenceContextValue>(presenceFallback);

/** The live counts, for the badge. */
export function usePresence(): PresenceState {
  const { pageCount, siteCount, connected } = useContext(PresenceContext);
  return { pageCount, siteCount, connected };
}

/** The cursor channel, for the cursor layer. */
export function usePresenceCursors(): Pick<
  PresenceContextValue,
  "sendCursor" | "subscribeToCursors" | "connected"
> {
  const { sendCursor, subscribeToCursors, connected } =
    useContext(PresenceContext);
  return { sendCursor, subscribeToCursors, connected };
}
