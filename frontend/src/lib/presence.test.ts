import { describe, expect, it } from "vitest";

import {
  BASE_RECONNECT_DELAY_MS,
  MAX_RECONNECT_DELAY_MS,
  parseInboundMessage,
  presenceUrl,
  reconnectDelayMs,
} from "@/lib/presence";

describe("presenceUrl", () => {
  it("follows the API base URL rather than being configured separately", () => {
    expect(presenceUrl("https://api.example.com")).toBe(
      "wss://api.example.com/ws/presence",
    );
  });

  it("stays on plain ws for a plain http API, as local development uses", () => {
    expect(presenceUrl("http://localhost:8080")).toBe(
      "ws://localhost:8080/ws/presence",
    );
  });

  it("only rewrites the scheme, not a host that happens to start with http", () => {
    expect(presenceUrl("https://http-api.example.com")).toBe(
      "wss://http-api.example.com/ws/presence",
    );
  });
});

describe("reconnectDelayMs", () => {
  it("doubles its ceiling with every attempt", () => {
    // Jitter pinned to its top, so the ceiling itself is visible.
    const atCeiling = (attempt: number) => reconnectDelayMs(attempt, () => 1);

    expect(atCeiling(0)).toBe(BASE_RECONNECT_DELAY_MS);
    expect(atCeiling(1)).toBe(2_000);
    expect(atCeiling(2)).toBe(4_000);
    expect(atCeiling(3)).toBe(8_000);
  });

  it("never waits longer than thirty seconds, however long the outage", () => {
    for (const attempt of [5, 10, 40, 1_000]) {
      expect(reconnectDelayMs(attempt, () => 1)).toBe(MAX_RECONNECT_DELAY_MS);
    }
  });

  it("spreads attempts across the lower half of the window", () => {
    // The jitter is the point: every open tab loses its socket in the same
    // millisecond when the backend goes down, and a bare doubling schedule
    // would have all of them retry together for the whole outage.
    expect(reconnectDelayMs(2, () => 0)).toBe(2_000);
    expect(reconnectDelayMs(2, () => 0.5)).toBe(3_000);
    expect(reconnectDelayMs(2, () => 1)).toBe(4_000);
  });

  it("stays positive at the bottom of the jitter window", () => {
    // A zero delay would be a reconnect storm rather than a backoff.
    expect(reconnectDelayMs(0, () => 0)).toBeGreaterThan(0);
  });
});

describe("parseInboundMessage", () => {
  it("reads a presence message", () => {
    const message = parseInboundMessage(
      '{"type":"presence","page":"/projects/githmind","pageCount":3,"siteCount":11}',
    );

    expect(message).toEqual({
      type: "presence",
      page: "/projects/githmind",
      pageCount: 3,
      siteCount: 11,
    });
  });

  it("reads a cursor message", () => {
    const message = parseInboundMessage(
      '{"type":"cursor","id":"anon-7f3a","x":0.42,"y":0.18}',
    );

    expect(message).toEqual({
      type: "cursor",
      id: "anon-7f3a",
      x: 0.42,
      y: 0.18,
    });
  });

  it("rejects a message type it has never heard of", () => {
    // A deployed tab can outlive the release that added a message, and
    // throwing on one would take presence down for everyone who had not
    // reloaded.
    expect(parseInboundMessage('{"type":"sparkle","hue":3}')).toBeNull();
  });

  it("rejects a known type with a field missing or of the wrong kind", () => {
    expect(
      parseInboundMessage('{"type":"presence","page":"/","pageCount":3}'),
    ).toBeNull();
    expect(
      parseInboundMessage(
        '{"type":"presence","page":"/","pageCount":"3","siteCount":4}',
      ),
    ).toBeNull();
    expect(parseInboundMessage('{"type":"cursor","x":0.1,"y":0.2}')).toBeNull();
    expect(
      parseInboundMessage('{"type":"cursor","id":"anon-1","x":"0.1","y":0.2}'),
    ).toBeNull();
  });

  it("rejects anything that is not a JSON object", () => {
    expect(parseInboundMessage("not json")).toBeNull();
    expect(parseInboundMessage("")).toBeNull();
    expect(parseInboundMessage("null")).toBeNull();
    expect(parseInboundMessage("[]")).toBeNull();
    expect(parseInboundMessage("42")).toBeNull();
  });

  it("rejects a frame that is not text at all", () => {
    // A Blob or an ArrayBuffer, which the server never sends.
    expect(parseInboundMessage(new ArrayBuffer(8))).toBeNull();
    expect(parseInboundMessage(undefined)).toBeNull();
    expect(parseInboundMessage(null)).toBeNull();
  });
});
