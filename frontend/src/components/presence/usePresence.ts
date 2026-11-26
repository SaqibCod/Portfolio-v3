"use client";

import { createContext, useContext } from "react";

/** What the badge reads, and all the socket publishes. */
export type PresenceState = {
  /** Visitors viewing this page, including this one. 0 while disconnected. */
  pageCount: number;
  /** Visitors anywhere on the site. 0 while disconnected. */
  siteCount: number;
  /** Whether the socket is open. Counts mean nothing when this is false. */
  connected: boolean;
};

/**
 * Disconnected, and inert. This is what the hook returns when there is no
 * provider above it, which is the right answer rather than a thrown error:
 * presence is a decoration, and a component that renders it must not break a
 * page -- or a test -- that has not opted into a socket.
 */
export const presenceFallback: PresenceState = {
  pageCount: 0,
  siteCount: 0,
  connected: false,
};

export const PresenceContext = createContext<PresenceState>(presenceFallback);

/** The live counts, for the badge. */
export function usePresence(): PresenceState {
  return useContext(PresenceContext);
}
