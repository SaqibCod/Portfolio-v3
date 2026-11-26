import { env } from "@/lib/env";

/**
 * The `/ws/presence` message format.
 *
 * Hand-written, unlike every other API type on the site, because there is
 * nothing to generate these from: WebSocket messages are not expressible in
 * OpenAPI, so the contract documents them in prose (its "Realtime presence"
 * section) rather than as schemas, and `openapi-typescript` has nothing to
 * read. These declarations are the only copy of that shape on this side, so
 * they have to be kept in step with the contract by hand.
 */

/** Client -> server, on connect and on every route change. */
export type JoinMessage = { type: "join"; page: string };

/** Client -> server, every 25 seconds. */
export type PingMessage = { type: "ping" };

export type OutboundMessage = JoinMessage | PingMessage;

/** Server -> client, whenever a page's counts change. */
export type PresenceMessage = {
  type: "presence";
  page: string;
  pageCount: number;
  siteCount: number;
};

/** The only thing the server sends. */
export type InboundMessage = PresenceMessage;

/**
 * The presence endpoint, derived from the API base URL rather than configured
 * separately. One variable cannot drift from the other, and a deployment that
 * moves the API moves presence with it.
 */
export function presenceUrl(apiBaseUrl: string = env.apiBaseUrl): string {
  return `${apiBaseUrl.replace(/^http/, "ws")}/ws/presence`;
}

/**
 * Narrows an incoming frame, discarding anything that is not a message this
 * version understands.
 *
 * The server is ours, so this is not defensive about malice — it is about
 * version skew. A deployed browser tab can outlive a backend release, and a
 * client that throws on a message type it has never seen would take the whole
 * feature down for anyone who had not reloaded. That cuts both ways: a tab
 * still running the version of this file that knew about live cursors will
 * receive none, and a tab running this one would discard them.
 */
export function parseInboundMessage(data: unknown): InboundMessage | null {
  if (typeof data !== "string") return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(data);
  } catch {
    return null;
  }

  if (typeof parsed !== "object" || parsed === null) return null;
  const message = parsed as Record<string, unknown>;

  if (
    message.type === "presence" &&
    typeof message.page === "string" &&
    typeof message.pageCount === "number" &&
    typeof message.siteCount === "number"
  ) {
    return message as PresenceMessage;
  }

  return null;
}

/** How often the client beats. The server closes a connection quiet for 60s. */
export const PING_INTERVAL_MS = 25_000;

/** First reconnect delay; doubles up to {@link MAX_RECONNECT_DELAY_MS}. */
export const BASE_RECONNECT_DELAY_MS = 1_000;

export const MAX_RECONNECT_DELAY_MS = 30_000;

/** A hidden tab is disconnected after this long, and reconnects when it returns. */
export const HIDDEN_DISCONNECT_MS = 5 * 60_000;

/**
 * Exponential backoff with full jitter, capped at 30 seconds.
 *
 * The jitter is the point, not a flourish. Every open tab loses its socket at
 * the same instant when the backend goes down, so a bare doubling schedule has
 * all of them retrying in the same millisecond for as long as the outage
 * lasts — which is precisely the load a recovering server least wants. Spread
 * across the window, the same tabs arrive evenly.
 */
export function reconnectDelayMs(
  attempt: number,
  random: () => number = Math.random,
): number {
  const ceiling = Math.min(
    BASE_RECONNECT_DELAY_MS * 2 ** attempt,
    MAX_RECONNECT_DELAY_MS,
  );
  return Math.round(ceiling * (0.5 + random() * 0.5));
}
