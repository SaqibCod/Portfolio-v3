import { afterEach, describe, expect, it, vi } from "vitest";

import { streamChat, stripMarkdown } from "@/lib/chat";

import type { ChatStreamEvent } from "@/lib/chat";

/** Builds an SSE response body from already-formatted event blocks. */
function sseResponse(blocks: string[], init: ResponseInit = {}): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder();
      for (const block of blocks) controller.enqueue(encoder.encode(block));
      controller.close();
    },
  });

  return new Response(body, {
    status: 200,
    headers: { "Content-Type": "text/event-stream" },
    ...init,
  });
}

function event(name: string, data: unknown): string {
  return `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`;
}

/** Collects every event `streamChat` reports, in order. */
async function collect(response: Response | Error): Promise<ChatStreamEvent[]> {
  const fetchMock = vi.fn(() =>
    response instanceof Error ? Promise.reject(response) : Promise.resolve(response),
  );
  vi.stubGlobal("fetch", fetchMock);

  const events: ChatStreamEvent[] = [];
  await streamChat([{ role: "user", content: "What does Saq build?" }], (e) => events.push(e));
  return events;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("streamChat", () => {
  it("reports tokens, sources and done in the order they arrive", async () => {
    const events = await collect(
      sseResponse([
        event("token", { text: "Saq " }),
        event("token", { text: "builds things." }),
        event("sources", { sources: [{ title: "About", url: "/#about" }] }),
        event("done", { model: "primary" }),
      ]),
    );

    expect(events).toEqual([
      { type: "token", text: "Saq " },
      { type: "token", text: "builds things." },
      { type: "sources", sources: [{ title: "About", url: "/#about" }] },
      { type: "done", model: "primary" },
    ]);
  });

  it("reassembles an event split across two network chunks", async () => {
    // A real stream has no respect for event boundaries, which is the entire
    // reason this uses a parser rather than splitting on newlines.
    const events = await collect(
      sseResponse(['event: token\ndata: {"text":"spl', 'it"}\n\n', event("done", { model: "primary" })]),
    );

    expect(events).toEqual([
      { type: "token", text: "split" },
      { type: "done", model: "primary" },
    ]);
  });

  it("reports which model answered", async () => {
    const events = await collect(
      sseResponse([event("token", { text: "hi" }), event("done", { model: "fallback" })]),
    );

    expect(events.at(-1)).toEqual({ type: "done", model: "fallback" });
  });

  it("ignores an event type it does not know", async () => {
    // Adding an event to the contract must not break a page that is already
    // deployed, so anything unrecognised is skipped rather than rendered.
    const events = await collect(
      sseResponse([event("heartbeat", { at: 1 }), event("done", { model: "primary" })]),
    );

    expect(events).toEqual([{ type: "done", model: "primary" }]);
  });

  it("turns a mid-stream error event into an error", async () => {
    const events = await collect(
      sseResponse([
        event("token", { text: "Half an answer" }),
        event("error", {
          type: "https://api.example.com/problems/upstream-unavailable",
          title: "Service unavailable",
          status: 503,
          detail: "The answer was cut short.",
        }),
      ]),
    );

    expect(events).toEqual([
      { type: "token", text: "Half an answer" },
      { type: "error", message: "The answer was cut short." },
    ]);
  });

  it("reports a stream that ends without done as incomplete", async () => {
    // The server died, or a proxy timed out. Without this the panel would sit
    // on a half-written answer with its typing indicator still running.
    const events = await collect(sseResponse([event("token", { text: "Half an" })]));

    expect(events[1]).toMatchObject({ type: "error" });
    expect(events[1]).toHaveProperty("message", expect.stringContaining("cut short"));
  });

  it("surfaces the wait from a 429", async () => {
    const events = await collect(
      new Response('{"status":429}', {
        status: 429,
        headers: { "Retry-After": "45", "Content-Type": "application/problem+json" },
      }),
    );

    expect(events).toEqual([
      { type: "error", message: "That's a lot of questions.", retryAfterSeconds: 45 },
    ]);
  });

  it("copes with a 429 that carries no Retry-After", async () => {
    const events = await collect(new Response("{}", { status: 429 }));

    expect(events[0]).toMatchObject({ type: "error", retryAfterSeconds: undefined });
  });

  it("uses the resting copy for a 503", async () => {
    const events = await collect(
      new Response('{"status":503}', {
        status: 503,
        headers: { "Content-Type": "application/problem+json" },
      }),
    );

    // Fixed copy rather than the Problem's detail: the server's wording is for
    // a log, and this one has to send the visitor somewhere else.
    expect(events[0]).toEqual({
      type: "error",
      message: "The assistant is resting. Try again soon or use the contact form.",
    });
  });

  it("reports a network failure rather than throwing", async () => {
    // Every failure is an event, so a caller needs one render path for
    // problems instead of a catch and a branch.
    const events = await collect(new TypeError("Failed to fetch"));

    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ type: "error" });
  });

  it("says nothing when the request is aborted", async () => {
    const controller = new AbortController();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new DOMException("aborted", "AbortError"))),
    );

    const events: ChatStreamEvent[] = [];
    controller.abort();
    await streamChat([{ role: "user", content: "q" }], (e) => events.push(e), controller.signal);

    // The only things that abort are closing the panel and asking something
    // else. Neither is a problem the visitor needs told about.
    expect(events).toEqual([]);
  });

  it("posts the conversation to the chat endpoint as JSON", async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(sseResponse([event("done", { model: "primary" })])),
    );
    vi.stubGlobal("fetch", fetchMock);

    await streamChat(
      [
        { role: "user", content: "What is githMind?" },
        { role: "assistant", content: "A repo assistant." },
        { role: "user", content: "What is it written in?" },
      ],
      () => {},
    );

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://api.example.com/api/v1/chat");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({
      messages: [
        { role: "user", content: "What is githMind?" },
        { role: "assistant", content: "A repo assistant." },
        { role: "user", content: "What is it written in?" },
      ],
    });
  });
});

describe("stripMarkdown", () => {
  it("removes bold fences a model leaves in", () => {
    // Observed for real: gpt-oss on the fallback writes "**Medi-Scan**" however firmly
    // the system prompt asks for plain prose.
    expect(stripMarkdown("Saq built **Medi-Scan**, an analyser.")).toBe(
      "Saq built Medi-Scan, an analyser.",
    );
  });

  it("removes underscore emphasis and inline code fences", () => {
    expect(stripMarkdown("He uses __Spring AI__ with `pgvector`.")).toBe(
      "He uses Spring AI with pgvector.",
    );
  });

  it("turns bullet lines into plain lines", () => {
    expect(stripMarkdown("Projects:\n- Medi-Scan\n* githMind")).toBe(
      "Projects:\nMedi-Scan\ngithMind",
    );
  });

  it("removes heading markers", () => {
    expect(stripMarkdown("## Overview\nHe builds systems.")).toBe(
      "Overview\nHe builds systems.",
    );
  });

  it("leaves ordinary prose untouched", () => {
    const prose = "Saq is an MCA student at IIT Patna, open to internships.";
    expect(stripMarkdown(prose)).toBe(prose);
  });

  it("leaves a lone asterisk and a hyphenated word alone", () => {
    // Only unambiguous syntax is stripped: a mid-sentence hyphen is not a bullet, and
    // a single asterisk is not an emphasis fence.
    expect(stripMarkdown("The retrieval-augmented answer scored 5 * 3 points.")).toBe(
      "The retrieval-augmented answer scored 5 * 3 points.",
    );
  });

  it("strips a fence that arrived split across two tokens", () => {
    // The hook accumulates raw text before stripping for exactly this reason.
    expect(stripMarkdown("Saq built *" + "*Medi-Scan**.")).toBe("Saq built Medi-Scan.");
  });
});
