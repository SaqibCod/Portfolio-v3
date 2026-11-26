import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ChatPanel } from "@/components/chat/ChatPanel";

import type { ChatStreamEvent } from "@/lib/chat";

const streamChat = vi.hoisted(() => vi.fn());

// The transport is mocked, not the network: these tests are about what the
// panel renders for a given sequence of events, and `chat.test.ts` already
// proves that sequence is what an SSE body produces.
vi.mock("@/lib/chat", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/chat")>()),
  streamChat,
}));

/** Replays a scripted sequence of events into the hook. */
function script(...events: ChatStreamEvent[]) {
  streamChat.mockImplementation(
    async (_messages: unknown, onEvent: (event: ChatStreamEvent) => void) => {
      for (const event of events) onEvent(event);
    },
  );
}

const ANSWER: ChatStreamEvent[] = [
  { type: "token", text: "Saq built " },
  { type: "token", text: "Medi-Scan with Spring AI." },
  {
    type: "sources",
    sources: [
      { title: "Medi-Scan case study", url: "/projects/medi-scan", snippet: "An AI analyser." },
    ],
  },
  { type: "done", model: "primary" },
];

beforeEach(() => {
  streamChat.mockReset();
  // jsdom has no matchMedia; the panel asks it whether to autofocus.
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ChatPanel", () => {
  it("starts from a question the palette handed over, without sending it", () => {
    render(
      <ChatPanel onClose={vi.fn()} initialQuestion="what is pgvector for" />,
    );

    expect(screen.getByRole("textbox", { name: /ask a question/i })).toHaveValue(
      "what is pgvector for",
    );
    // A search that matched nothing must not become a request on its own.
    expect(streamChat).not.toHaveBeenCalled();
  });

  it("offers three openers before anything has been asked", () => {
    render(<ChatPanel onClose={vi.fn()} />);

    // An empty panel with only a text field gives a visitor nothing to go on;
    // the suggestions are what show the range of what the assistant knows.
    expect(screen.getByRole("button", { name: /spring ai/i })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /saq|site/i }).length).toBeGreaterThanOrEqual(3);
  });

  it("streams an answer into an assistant bubble", async () => {
    script(...ANSWER);
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.type(
      screen.getByLabelText("Ask a question"),
      "What has Saq built with Spring AI?{Enter}",
    );

    const log = within(screen.getByRole("log"));
    expect(await log.findByText("What has Saq built with Spring AI?")).toBeInTheDocument();
    // Scoped to the log because the finished answer is also written into the
    // panel's live region, on purpose: a screen reader hears it once, at the
    // end, rather than a token at a time.
    expect(await log.findByText("Saq built Medi-Scan with Spring AI.")).toBeInTheDocument();
  });

  it("links the pages the answer cited", async () => {
    script(...ANSWER);
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: /spring ai/i }));

    const chip = await screen.findByRole("link", { name: /medi-scan case study/i });
    // A real link, so a citation can be middle-clicked into a new tab while
    // the visitor keeps reading.
    expect(chip).toHaveAttribute("href", "/projects/medi-scan");
  });

  it("sends a suggestion when it is clicked", async () => {
    script(...ANSWER);
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: /spring ai/i }));

    await waitFor(() => expect(streamChat).toHaveBeenCalledTimes(1));
    expect(streamChat.mock.calls[0][0]).toEqual([
      { role: "user", content: "What has Saq built with Spring AI?" },
    ]);
  });

  it("carries the earlier turns into the next question", async () => {
    script(...ANSWER);
    render(<ChatPanel onClose={vi.fn()} />);

    const input = screen.getByLabelText("Ask a question");
    await userEvent.type(input, "What is githMind?{Enter}");
    await waitFor(() => expect(streamChat).toHaveBeenCalledTimes(1));

    await userEvent.type(input, "What is it written in?{Enter}");
    await waitFor(() => expect(streamChat).toHaveBeenCalledTimes(2));

    // The endpoint is stateless, so "it" only resolves if the client resends
    // the turns that gave it a referent.
    expect(streamChat.mock.calls[1][0]).toEqual([
      { role: "user", content: "What is githMind?" },
      { role: "assistant", content: "Saq built Medi-Scan with Spring AI." },
      { role: "user", content: "What is it written in?" },
    ]);
  });

  it("clears the field once a question is sent", async () => {
    script(...ANSWER);
    render(<ChatPanel onClose={vi.fn()} />);

    const input = screen.getByLabelText("Ask a question");
    await userEvent.type(input, "A question{Enter}");

    await waitFor(() => expect(input).toHaveValue(""));
  });

  it("will not send an empty question", async () => {
    render(<ChatPanel onClose={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Send question" })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Ask a question"), "   {Enter}");

    expect(streamChat).not.toHaveBeenCalled();
  });

  it("stops the visitor at the contract's character limit", async () => {
    render(<ChatPanel onClose={vi.fn()} />);

    // maxLength rather than a validation message: the server would answer 400,
    // and there is nothing to explain about a limit the field can simply hold.
    expect(screen.getByLabelText("Ask a question")).toHaveAttribute("maxLength", "1000");
  });

  it("shows the resting message when the assistant is unavailable", async () => {
    script({
      type: "error",
      message: "The assistant is resting. Try again soon or use the contact form.",
    });
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: /spring ai/i }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/resting/);
    expect(alert).toHaveTextContent(/contact form/);
  });

  it("tells the visitor how long to wait after a 429", async () => {
    script({ type: "error", message: "That's a lot of questions.", retryAfterSeconds: 45 });
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: /spring ai/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Try again in 45 seconds.");
  });

  it("rounds a long wait up to whole minutes", async () => {
    script({ type: "error", message: "That's a lot of questions.", retryAfterSeconds: 90 });
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: /spring ai/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Try again in 2 minutes.");
  });

  it("drops the empty bubble when the answer never arrived", async () => {
    script({ type: "error", message: "The assistant is resting." });
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.type(screen.getByLabelText("Ask a question"), "A question{Enter}");

    await screen.findByRole("alert");
    // The question stays; the blank answer below it does not.
    expect(screen.getByText("A question")).toBeInTheDocument();
    expect(screen.getByRole("log").querySelectorAll("p.whitespace-pre-wrap")).toHaveLength(1);
  });

  it("renders an answer as text, never as markup", async () => {
    script(
      { type: "token", text: "<img src=x onerror=alert(1)>" },
      { type: "done", model: "primary" },
    );
    render(<ChatPanel onClose={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: /spring ai/i }));

    // The answer comes from a model reading site content. Nothing on that path
    // is trusted enough to render as HTML.
    const log = within(screen.getByRole("log"));
    expect(await log.findByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
    expect(screen.getByRole("log").querySelector("img")).toBeNull();
  });

  it("names the disclaimer the export asks for", () => {
    render(<ChatPanel onClose={vi.fn()} />);

    expect(
      screen.getByText("Answers come from this site's content · up to 10 messages"),
    ).toBeInTheDocument();
  });

  it("closes on the header button", async () => {
    const onClose = vi.fn();
    render(<ChatPanel onClose={onClose} />);

    await userEvent.click(screen.getByRole("button", { name: "Close chat" }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes on Escape", async () => {
    const onClose = vi.fn();
    render(<ChatPanel onClose={onClose} />);

    await userEvent.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("is a labelled dialog", () => {
    render(<ChatPanel onClose={vi.fn()} />);

    expect(screen.getByRole("dialog", { name: "Ask about me" })).toBeInTheDocument();
    // A log rather than a live region: announcing every token would make a
    // screen reader unusable while an answer streams.
    expect(screen.getByRole("log", { name: "Conversation" })).toBeInTheDocument();
  });
});
