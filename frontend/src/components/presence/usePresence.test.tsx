import { act, cleanup, render, screen } from "@testing-library/react";
import { useEffect } from "react";
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
  type MockInstance,
} from "vitest";

import { PresenceProvider } from "@/components/presence/PresenceProvider";
import {
  usePresence,
  usePresenceCursors,
} from "@/components/presence/usePresence";
import { PING_INTERVAL_MS } from "@/lib/presence";

const pathname = vi.hoisted(() => ({ current: "/projects/githmind" }));

vi.mock("next/navigation", () => ({
  usePathname: () => pathname.current,
}));

/**
 * A WebSocket that does nothing until a test tells it to.
 *
 * The real thing cannot be driven from a test — there is no way to make a
 * browser socket open, deliver a frame or drop at a chosen moment — so the
 * class is replaced and the test plays the server's part. Every instance is
 * recorded, which is what lets the reconnect assertions count connections.
 */
class FakeWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;

  static instances: FakeWebSocket[] = [];

  readyState = FakeWebSocket.CONNECTING;

  readonly sent: string[] = [];

  readonly url: string;

  private readonly listeners = new Map<string, Set<(event: unknown) => void>>();

  constructor(url: string) {
    this.url = url;
    FakeWebSocket.instances.push(this);
  }

  addEventListener(type: string, listener: (event: unknown) => void) {
    const existing = this.listeners.get(type) ?? new Set();
    existing.add(listener);
    this.listeners.set(type, existing);
  }

  removeEventListener(type: string, listener: (event: unknown) => void) {
    this.listeners.get(type)?.delete(listener);
  }

  send(payload: string) {
    this.sent.push(payload);
  }

  close() {
    if (this.readyState === FakeWebSocket.CLOSED) return;
    this.readyState = FakeWebSocket.CLOSED;
    this.emit("close", {});
  }

  /** The server accepting the handshake. */
  open() {
    this.readyState = FakeWebSocket.OPEN;
    this.emit("open", {});
  }

  /** The server sending a frame. */
  receive(message: unknown) {
    this.emit("message", { data: JSON.stringify(message) });
  }

  /** A raw frame, for the parser's benefit. */
  receiveRaw(data: unknown) {
    this.emit("message", { data });
  }

  /** The connection dropping without anyone asking. */
  drop() {
    this.readyState = FakeWebSocket.CLOSED;
    this.emit("error", {});
    this.emit("close", {});
  }

  /** What this socket sent, parsed. */
  messages(): Array<Record<string, unknown>> {
    return this.sent.map((payload) => JSON.parse(payload));
  }

  private emit(type: string, event: unknown) {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event);
    }
  }

  static latest(): FakeWebSocket {
    const socket = FakeWebSocket.instances.at(-1);
    if (!socket) throw new Error("No socket was opened");
    return socket;
  }
}

/** Prints the hook's state, so assertions read it off the DOM. */
function PresenceReadout() {
  const { pageCount, siteCount, connected } = usePresence();
  return (
    <p data-testid="readout">{`${connected ? "on" : "off"}:${pageCount}:${siteCount}`}</p>
  );
}

function renderProvider(children = <PresenceReadout />) {
  return render(<PresenceProvider>{children}</PresenceProvider>);
}

function readout() {
  return screen.getByTestId("readout").textContent;
}

describe("presence provider and hooks", () => {
  let idleCallbacks: Array<() => void>;
  let randomSpy: MockInstance<() => number>;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeWebSocket.instances = [];
    pathname.current = "/projects/githmind";

    vi.stubGlobal("WebSocket", FakeWebSocket);

    // The provider connects on the first idle callback; collecting them lets a
    // test decide when that is, and prove it does not happen before.
    idleCallbacks = [];
    vi.stubGlobal("requestIdleCallback", (callback: () => void) => {
      idleCallbacks.push(callback);
      return idleCallbacks.length;
    });
    vi.stubGlobal("cancelIdleCallback", () => {});

    // Jitter at its midpoint, so backoff delays are exact numbers.
    randomSpy = vi.spyOn(Math, "random").mockReturnValue(0.5);
  });

  afterEach(() => {
    // Unmounting first, deliberately. The global setup file's cleanup() runs
    // after this hook, and by then the stubs would be gone -- so the
    // provider's teardown would reach for a cancelIdleCallback that no longer
    // exists and fail every test in this file during its own cleanup.
    cleanup();
    randomSpy.mockRestore();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  /** Runs the deferred connect, leaving the handshake outstanding. */
  function startConnecting() {
    act(() => {
      idleCallbacks.forEach((callback) => callback());
      idleCallbacks = [];
    });
    return FakeWebSocket.latest();
  }

  /** Runs the deferred connect and accepts the handshake. */
  function connect() {
    const socket = startConnecting();
    act(() => socket.open());
    return socket;
  }

  // --- connecting ---------------------------------------------------------

  it("does not open a socket until the browser is idle", () => {
    renderProvider();

    // Hydration, fonts and the first paint come first; this is a socket for a
    // decoration.
    expect(FakeWebSocket.instances).toHaveLength(0);
    expect(readout()).toBe("off:0:0");
  });

  it("connects to the API's host over ws", () => {
    renderProvider();
    const socket = connect();

    // Derived from NEXT_PUBLIC_API_BASE_URL rather than configured separately,
    // so the two cannot drift.
    expect(socket.url).toBe("wss://api.example.com/ws/presence");
  });

  it("announces the current page as soon as it is open", () => {
    renderProvider();
    const socket = connect();

    expect(socket.messages()).toContainEqual({
      type: "join",
      page: "/projects/githmind",
    });
  });

  it("reports the counts the server sends", () => {
    renderProvider();
    const socket = connect();

    act(() =>
      socket.receive({
        type: "presence",
        page: "/projects/githmind",
        pageCount: 3,
        siteCount: 11,
      }),
    );

    expect(readout()).toBe("on:3:11");
  });

  it("ignores counts for a page this tab is not on", () => {
    renderProvider();
    const socket = connect();

    // The server broadcasts per page, and a message about the page just left
    // can arrive after the navigation.
    act(() =>
      socket.receive({
        type: "presence",
        page: "/guestbook",
        pageCount: 9,
        siteCount: 9,
      }),
    );

    expect(readout()).toBe("on:0:0");
  });

  it("ignores frames it cannot make sense of", () => {
    renderProvider();
    const socket = connect();

    // Version skew, not malice: a tab can outlive the release that taught the
    // server a new message, and throwing would take the feature down for it.
    act(() => {
      socket.receiveRaw("not json");
      socket.receiveRaw(JSON.stringify({ type: "something-new" }));
      socket.receive({ type: "presence", page: "/projects/githmind" });
      socket.receiveRaw(null);
    });

    expect(readout()).toBe("on:0:0");
  });

  // --- heartbeat ----------------------------------------------------------

  it("beats every 25 seconds while open", () => {
    renderProvider();
    const socket = connect();

    act(() => vi.advanceTimersByTime(PING_INTERVAL_MS * 3));

    expect(socket.messages().filter((m) => m.type === "ping")).toHaveLength(3);
  });

  it("stops beating once the socket has gone", () => {
    renderProvider();
    const socket = connect();
    act(() => socket.drop());

    act(() => vi.advanceTimersByTime(PING_INTERVAL_MS * 2));

    expect(socket.messages().filter((m) => m.type === "ping")).toHaveLength(0);
  });

  // --- navigation ---------------------------------------------------------

  it("sends a join on every route change, on the same socket", () => {
    const { rerender } = renderProvider();
    const socket = connect();

    pathname.current = "/guestbook";
    act(() => {
      rerender(
        <PresenceProvider>
          <PresenceReadout />
        </PresenceProvider>,
      );
    });

    expect(socket.messages()).toContainEqual({
      type: "join",
      page: "/guestbook",
    });
    // One socket for the session, not one per page.
    expect(FakeWebSocket.instances).toHaveLength(1);
  });

  it("drops the old page's counts the moment the route changes", () => {
    const { rerender } = renderProvider();
    const socket = connect();
    act(() =>
      socket.receive({
        type: "presence",
        page: "/projects/githmind",
        pageCount: 4,
        siteCount: 4,
      }),
    );
    expect(readout()).toBe("on:4:4");

    pathname.current = "/guestbook";
    act(() => {
      rerender(
        <PresenceProvider>
          <PresenceReadout />
        </PresenceProvider>,
      );
    });

    // Four people were on the page just left, which says nothing about this one.
    expect(readout()).toBe("on:0:0");
  });

  // --- losing the connection ----------------------------------------------

  it("reports nothing and shows no error when the backend dies", () => {
    renderProvider();
    const socket = connect();
    act(() =>
      socket.receive({
        type: "presence",
        page: "/projects/githmind",
        pageCount: 5,
        siteCount: 5,
      }),
    );

    act(() => socket.drop());

    expect(readout()).toBe("off:0:0");
  });

  it("reconnects on a backoff that doubles", () => {
    renderProvider();
    startConnecting();
    act(() => FakeWebSocket.latest().drop());
    expect(FakeWebSocket.instances).toHaveLength(1);

    // Ceilings of 1s, 2s and 4s, each taken at the midpoint of its jitter
    // window (Math.random pinned to 0.5), so 750ms, 1.5s and 3s. The doubling
    // is the subject here; `presence.test.ts` pins the arithmetic itself.
    act(() => vi.advanceTimersByTime(749));
    expect(FakeWebSocket.instances).toHaveLength(1);
    act(() => vi.advanceTimersByTime(1));
    expect(FakeWebSocket.instances).toHaveLength(2);

    act(() => FakeWebSocket.latest().drop());
    act(() => vi.advanceTimersByTime(1_499));
    expect(FakeWebSocket.instances).toHaveLength(2);
    act(() => vi.advanceTimersByTime(1));
    expect(FakeWebSocket.instances).toHaveLength(3);

    act(() => FakeWebSocket.latest().drop());
    act(() => vi.advanceTimersByTime(2_999));
    expect(FakeWebSocket.instances).toHaveLength(3);
    act(() => vi.advanceTimersByTime(1));
    expect(FakeWebSocket.instances).toHaveLength(4);
  });

  it("re-announces the page it is on after reconnecting", () => {
    renderProvider();
    connect();
    act(() => FakeWebSocket.latest().drop());

    act(() => vi.advanceTimersByTime(750));
    act(() => FakeWebSocket.latest().open());

    expect(FakeWebSocket.latest().messages()).toContainEqual({
      type: "join",
      page: "/projects/githmind",
    });
  });

  it("starts the backoff over after a connection succeeds", () => {
    renderProvider();
    startConnecting();
    // Fail twice, so the delay has grown past its first step.
    act(() => FakeWebSocket.latest().drop());
    act(() => vi.advanceTimersByTime(750));
    act(() => FakeWebSocket.latest().drop());
    act(() => vi.advanceTimersByTime(1_500));

    // Then succeed, and fail once more: the next retry is back to 750ms, not
    // the 3s the attempt counter had climbed to.
    act(() => FakeWebSocket.latest().open());
    act(() => FakeWebSocket.latest().drop());
    const before = FakeWebSocket.instances.length;
    act(() => vi.advanceTimersByTime(750));

    expect(FakeWebSocket.instances).toHaveLength(before + 1);
  });

  // --- hidden tabs --------------------------------------------------------

  it("keeps the socket through a brief tab switch", () => {
    renderProvider();
    const socket = connect();

    hide();
    act(() => vi.advanceTimersByTime(60_000));

    // A minute away is still a person reading the site.
    expect(socket.readyState).toBe(FakeWebSocket.OPEN);
  });

  it("disconnects a tab hidden for five minutes", () => {
    renderProvider();
    const socket = connect();

    hide();
    act(() => vi.advanceTimersByTime(5 * 60_000));

    expect(socket.readyState).toBe(FakeWebSocket.CLOSED);
    expect(readout()).toBe("off:0:0");
  });

  it("does not reconnect a tab it put to sleep itself", () => {
    renderProvider();
    connect();
    const count = FakeWebSocket.instances.length;

    hide();
    act(() => vi.advanceTimersByTime(5 * 60_000));
    act(() => vi.advanceTimersByTime(60_000));

    // Otherwise closing the socket would immediately undo itself.
    expect(FakeWebSocket.instances).toHaveLength(count);
  });

  it("reconnects when a sleeping tab comes back", () => {
    renderProvider();
    connect();
    hide();
    act(() => vi.advanceTimersByTime(5 * 60_000));
    const count = FakeWebSocket.instances.length;

    show();

    expect(FakeWebSocket.instances).toHaveLength(count + 1);
  });

  // --- cursors ------------------------------------------------------------

  it("sends a cursor as the contract spells it", () => {
    function CursorSender() {
      const { sendCursor } = usePresenceCursors();
      return (
        <button type="button" onClick={() => sendCursor(0.42, 0.18)}>
          move
        </button>
      );
    }
    renderProvider(<CursorSender />);
    const socket = connect();

    act(() => screen.getByRole("button").click());

    expect(socket.messages()).toContainEqual({
      type: "cursor",
      x: 0.42,
      y: 0.18,
    });
  });

  it("delivers other visitors' cursors to subscribers, not to state", () => {
    const seen: unknown[] = [];
    // Counted in an effect with no dependency list, which runs after every
    // render. Incrementing during render would be a render-phase mutation.
    const renders: true[] = [];

    function CursorWatcher() {
      const { subscribeToCursors } = usePresenceCursors();
      useEffect(() => {
        renders.push(true);
      });
      useEffect(
        () => subscribeToCursors((cursor) => seen.push(cursor)),
        [subscribeToCursors],
      );
      return <p data-testid="readout">watching</p>;
    }

    renderProvider(<CursorWatcher />);
    const socket = connect();
    const rendersBefore = renders.length;

    act(() => {
      socket.receive({ type: "cursor", id: "anon-7f3a", x: 0.1, y: 0.2 });
      socket.receive({ type: "cursor", id: "anon-7f3a", x: 0.3, y: 0.4 });
    });

    expect(seen).toEqual([
      { type: "cursor", id: "anon-7f3a", x: 0.1, y: 0.2 },
      { type: "cursor", id: "anon-7f3a", x: 0.3, y: 0.4 },
    ]);
    // Twenty cursor messages a second per visitor must not re-render the site.
    expect(renders).toHaveLength(rendersBefore);
  });

  it("drops a cursor send while the socket is not open", () => {
    function CursorSender() {
      const { sendCursor } = usePresenceCursors();
      return (
        <button type="button" onClick={() => sendCursor(0.5, 0.5)}>
          move
        </button>
      );
    }
    renderProvider(<CursorSender />);

    // Never opened: the caller should not have to check first.
    act(() => screen.getByRole("button").click());

    expect(FakeWebSocket.instances).toHaveLength(0);
  });
});

function hide() {
  setVisibility("hidden");
}

function show() {
  setVisibility("visible");
}

function setVisibility(state: "hidden" | "visible") {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => state,
  });
  act(() => {
    document.dispatchEvent(new Event("visibilitychange"));
  });
}
