"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { MAX_MESSAGES, streamChat } from "@/lib/chat";

import type { ChatMessage, ChatSource } from "@/lib/chat";

/** One bubble in the panel. */
export type ChatTurn = {
  role: "user" | "assistant";
  content: string;
  /** Cited pages. Present on an assistant turn once its `sources` event lands. */
  sources?: ChatSource[];
  /** Which model answered, for the one case worth telling the visitor about. */
  model?: string;
};

export type ChatError = {
  message: string;
  /** Seconds to wait, from a 429's `Retry-After`. */
  retryAfterSeconds?: number;
};

export type UseChatStream = {
  turns: ChatTurn[];
  /** True from the moment a question is sent until the stream ends. */
  isStreaming: boolean;
  /** True while the request is in flight but no token has arrived yet. */
  isThinking: boolean;
  error: ChatError | null;
  ask: (question: string) => void;
  /** Abandons the answer in progress, keeping what has been read so far. */
  stop: () => void;
};

/**
 * Drives one conversation with the assistant.
 *
 * Holds the whole conversation, because the endpoint is stateless: every
 * question resends the recent turns, and the server reads the last one as the
 * question. The trim to {@link MAX_MESSAGES} happens here rather than being
 * left to the API's 400 — a visitor who asks an eleventh question should get
 * an answer, not a validation error about a limit they cannot see.
 *
 * One answer at a time. A second question aborts the first, which is what a
 * visitor means when they interrupt: the half-written answer stays on screen,
 * and the request behind it stops costing tokens.
 */
export function useChatStream(): UseChatStream {
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isThinking, setIsThinking] = useState(false);
  const [error, setError] = useState<ChatError | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  // The conversation as the request needs it. A ref as well as state because
  // `ask` must read the current history without being re-created on every
  // token — which would re-render the input on every frame of the answer.
  const historyRef = useRef<ChatMessage[]>([]);

  // Closing the panel, or navigating away, must not leave a request running.
  useEffect(() => () => abortRef.current?.abort(), []);

  const stop = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
    setIsThinking(false);
  }, []);

  const ask = useCallback((question: string) => {
    const content = question.trim();
    if (!content) return;

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setError(null);
    setIsStreaming(true);
    setIsThinking(true);

    // The question, plus the empty bubble its answer streams into. Both are
    // added before the request so the panel reacts immediately.
    historyRef.current = [...historyRef.current, { role: "user" as const, content }]
      .slice(-MAX_MESSAGES);
    setTurns((previous) => [
      ...previous,
      { role: "user" as const, content },
      { role: "assistant", content: "" },
    ]);

    let answer = "";

    void streamChat(
      historyRef.current,
      (event) => {
        if (controller.signal.aborted) return;

        switch (event.type) {
          case "token":
            answer += event.text;
            setIsThinking(false);
            setTurns((previous) => replaceLastAssistant(previous, { content: answer }));
            break;

          case "sources":
            setTurns((previous) => replaceLastAssistant(previous, { sources: event.sources }));
            break;

          case "done":
            setTurns((previous) => replaceLastAssistant(previous, { model: event.model }));
            // Recorded so the next question carries this answer as context —
            // "what is it written in?" only works if the model sees what "it"
            // referred to.
            historyRef.current = [
              ...historyRef.current,
              { role: "assistant" as const, content: answer },
            ].slice(-MAX_MESSAGES);
            setIsStreaming(false);
            setIsThinking(false);
            break;

          case "error":
            setError({ message: event.message, retryAfterSeconds: event.retryAfterSeconds });
            setIsStreaming(false);
            setIsThinking(false);
            // A failed turn is dropped from the history, not kept. An empty or
            // half-written answer sent back as context would teach the model to
            // produce more of the same.
            setTurns((previous) => dropEmptyAssistant(previous));
            historyRef.current = historyRef.current.slice(0, -1);
            break;
        }
      },
      controller.signal,
    );
  }, []);

  return { turns, isStreaming, isThinking, error, ask, stop };
}

/** Patches the answer currently streaming, leaving every earlier turn alone. */
function replaceLastAssistant(turns: ChatTurn[], patch: Partial<ChatTurn>): ChatTurn[] {
  const last = turns.at(-1);
  if (!last || last.role !== "assistant") return turns;
  return [...turns.slice(0, -1), { ...last, ...patch }];
}

/** Removes the placeholder bubble when the answer never arrived. */
function dropEmptyAssistant(turns: ChatTurn[]): ChatTurn[] {
  const last = turns.at(-1);
  if (!last || last.role !== "assistant" || last.content !== "") return turns;
  return turns.slice(0, -1);
}
