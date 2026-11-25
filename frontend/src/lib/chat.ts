import { createParser } from "eventsource-parser";

import { env } from "@/lib/env";
import { isProblem, problemMessage } from "@/lib/api/problem";

import type { components } from "@/lib/api/schema";

/** One turn of the conversation, exactly as `ChatMessage` defines it. */
export type ChatMessage = components["schemas"]["ChatMessage"];

/** A page the assistant cited, exactly as `ChatSource` defines it. */
export type ChatSource = components["schemas"]["ChatSource"];

/** The contract's `ChatMessage.content` maxLength, reused by the input's counter. */
export const MAX_MESSAGE_LENGTH = 1000;

/** The contract's `ChatRequest.messages` maxItems. */
export const MAX_MESSAGES = 10;

/**
 * What the caller is told as the answer arrives.
 *
 * One discriminated union rather than four callbacks: the hook reducing these
 * can then handle every case exhaustively, and a new event type added to the
 * contract becomes a TypeScript error instead of a silently ignored branch.
 */
export type ChatStreamEvent =
  /** A fragment of the answer. Append it verbatim. */
  | { type: "token"; text: string }
  /** The cited pages. Arrives once, after the last token. */
  | { type: "sources"; sources: ChatSource[] }
  /** The answer is complete; `model` says which model wrote it. */
  | { type: "done"; model: string }
  /**
   * The request failed. `message` is ready to show a visitor;
   * `retryAfterSeconds` is set only for a 429.
   */
  | { type: "error"; message: string; retryAfterSeconds?: number };

/**
 * Strips the markdown a model emits despite being told not to.
 *
 * The panel renders answers as literal text — deliberately, because the text comes
 * from a language model and nothing on that path is trusted enough to render as
 * markup. The consequence is that any markdown the model writes reaches the visitor
 * with its syntax showing: `**Medi-Scan**` appears with the asterisks.
 *
 * The system prompt forbids it and Gemini obeys; `gpt-oss` on the fallback does not.
 * Since the set of models is expected to change and each one has its own formatting
 * habits, this is handled here rather than by prompting alone — a visible defect
 * should not depend on every future model agreeing to be polite.
 *
 * Only emphasis and list markers are touched, and only the characters that are
 * unambiguously syntax. Nothing is converted *to* markup, so the rendering stays
 * plain text either way.
 */
export function stripMarkdown(text: string): string {
  return (
    text
      // Bold and italic fences. Removed unconditionally rather than only in matched
      // pairs: a stray `**` is never meaningful prose, and waiting for the closing
      // fence would make the asterisks appear and then vanish mid-stream.
      .replace(/\*\*/g, "")
      .replace(/__/g, "")
      // Inline code fences, which the model sometimes puts around a technology name.
      .replace(/`/g, "")
      // Leading bullet and heading markers, kept as plain lines.
      .replace(/^[ \t]*[-*+][ \t]+/gm, "")
      .replace(/^[ \t]*#{1,6}[ \t]+/gm, "")
  );
}

/** Copy for the two failures a visitor can actually do something about. */
const UNAVAILABLE_MESSAGE =
  "The assistant is resting. Try again soon or use the contact form.";

const NETWORK_MESSAGE =
  "The assistant could not be reached. Check your connection and try again.";

const INCOMPLETE_MESSAGE =
  "That answer was cut short. Ask again and I'll have another go.";

/**
 * Posts a conversation and reports events as they arrive.
 *
 * Why raw `fetch` rather than the shared `apiClient`: `openapi-fetch` resolves
 * a whole response before handing it back, which is exactly what this endpoint
 * must not do — the point of SSE here is that the first words appear before the
 * answer exists. The types are still generated from the contract, so a renamed
 * field is a `pnpm typecheck` failure like anywhere else; only the transport
 * differs.
 *
 * Nothing throws. Every failure — HTTP, network, abort, a malformed stream —
 * arrives as an `error` event, so a caller has one place to render problems
 * instead of a `catch` and a branch.
 *
 * @param messages oldest first, at most {@link MAX_MESSAGES}, the last one the
 *                 visitor's question
 * @param onEvent  called for every event, in order
 * @param signal   aborts the request; an abort is silent, because the only
 *                 thing that aborts is the visitor closing the panel or asking
 *                 something else, and neither is a problem to report
 */
export async function streamChat(
  messages: ChatMessage[],
  onEvent: (event: ChatStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  let response: Response;

  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify({ messages }),
      signal,
    });
  } catch (error) {
    if (isAbort(error)) return;
    onEvent({ type: "error", message: NETWORK_MESSAGE });
    return;
  }

  if (!response.ok) {
    onEvent(await httpError(response));
    return;
  }

  if (!response.body) {
    onEvent({ type: "error", message: NETWORK_MESSAGE });
    return;
  }

  let sawDone = false;
  let sawError = false;

  const parser = createParser({
    onEvent: ({ event, data }) => {
      const parsed = parseEvent(event, data);
      if (!parsed) return;
      if (parsed.type === "done") sawDone = true;
      if (parsed.type === "error") sawError = true;
      onEvent(parsed);
    },
  });

  try {
    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      parser.feed(decoder.decode(value, { stream: true }));
    }
  } catch (error) {
    if (isAbort(error)) return;
    onEvent({ type: "error", message: INCOMPLETE_MESSAGE });
    return;
  }

  // A stream that ends without `done` was cut short — the server crashed, a
  // proxy timed out, the connection dropped. Without this the panel would sit
  // on a half-written answer with its typing indicator still running.
  if (!sawDone && !sawError) {
    onEvent({ type: "error", message: INCOMPLETE_MESSAGE });
  }
}

/** One SSE event as the contract defines it, or `null` for anything unknown. */
function parseEvent(event: string | undefined, data: string): ChatStreamEvent | null {
  let payload: unknown;
  try {
    payload = JSON.parse(data);
  } catch {
    // A truncated final chunk, or a keep-alive comment. Not worth surfacing:
    // the missing `done` check above catches a stream that really did break.
    return null;
  }

  switch (event) {
    case "token": {
      const { text } = payload as { text?: unknown };
      return typeof text === "string" ? { type: "token", text } : null;
    }
    case "sources": {
      const { sources } = payload as { sources?: unknown };
      return { type: "sources", sources: Array.isArray(sources) ? (sources as ChatSource[]) : [] };
    }
    case "done": {
      const { model } = payload as { model?: unknown };
      return { type: "done", model: typeof model === "string" ? model : "primary" };
    }
    case "error":
      return { type: "error", message: problemMessage(payload) };
    default:
      // An event type this client does not know about. Ignored rather than
      // rendered, so adding one to the contract cannot break a deployed page.
      return null;
  }
}

/**
 * A non-2xx response as an error event.
 *
 * The 503 copy is fixed rather than taken from the Problem's `detail`, because
 * the server's wording is for a developer reading a log and this one has to
 * give a visitor somewhere else to go.
 */
async function httpError(response: Response): Promise<ChatStreamEvent> {
  if (response.status === 429) {
    const header = response.headers.get("Retry-After");
    const seconds = header ? Number.parseInt(header, 10) : Number.NaN;
    return {
      type: "error",
      message: "That's a lot of questions.",
      retryAfterSeconds: Number.isFinite(seconds) && seconds > 0 ? seconds : undefined,
    };
  }

  if (response.status === 503) {
    return { type: "error", message: UNAVAILABLE_MESSAGE };
  }

  try {
    const problem: unknown = await response.json();
    if (isProblem(problem)) {
      return { type: "error", message: problemMessage(problem) };
    }
  } catch {
    // Fall through to the generic message.
  }

  return { type: "error", message: NETWORK_MESSAGE };
}

function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
