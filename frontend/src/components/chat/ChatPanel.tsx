"use client";

import { useEffect, useId, useRef, useState } from "react";

import { SourceChips } from "@/components/chat/SourceChips";
import { useChatStream } from "@/components/chat/useChatStream";
import { VisuallyHidden } from "@/components/ui/VisuallyHidden";
import { cn } from "@/lib/cn";
import { MAX_MESSAGE_LENGTH } from "@/lib/chat";
import { siteConfig } from "@/lib/site-config";

/**
 * Openers for the empty state. Three, as the phase file asks: enough to show
 * the range of what the assistant knows, few enough to read at a glance.
 * Each one is answerable from the knowledge base, so a visitor's first
 * impression is a grounded answer rather than "I don't have that on the site".
 */
const SUGGESTIONS = [
  "What has Saq built with Spring AI?",
  "What does Saq work with day to day?",
  "How is this site put together?",
] as const;

/**
 * The export's `.cnote2`. Deliberately the export's wording rather than the
 * phase file's "AI can be wrong": it says the same thing about trust while
 * also telling the visitor where answers come from and what the limit is.
 */
const DISCLAIMER = "Answers come from this site's content · up to 10 messages";

type ChatPanelProps = {
  /** Closes the panel. Called by the header button and by Escape. */
  onClose: () => void;
};

/**
 * `.chat` from the home export: the floating dialog with the message list, the
 * disclaimer and the ask field.
 *
 * A dialog rather than a region, because it behaves like one — it traps nothing
 * but it does own focus while open and Escape closes it. The content is
 * entirely derived: every answer restates what is already on the pages it
 * cites, which is what lets this whole component load after the page has
 * painted without costing the site anything a crawler needs.
 */
export function ChatPanel({ onClose }: ChatPanelProps) {
  const { turns, isStreaming, isThinking, error, ask, stop } = useChatStream();
  const [draft, setDraft] = useState("");

  const inputId = useId();
  const titleId = useId();
  const logRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Escape closes, from anywhere inside the panel. Bound on the window rather
  // than the panel so it still works while focus sits on a source chip.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  // The panel is opened by a deliberate click on a floating button, so moving
  // focus to the field is what the visitor already asked for. Skipped on small
  // screens, where it would raise the keyboard over the conversation.
  useEffect(() => {
    if (window.matchMedia("(min-width: 47.5rem)").matches) {
      inputRef.current?.focus();
    }
  }, []);

  // Follows the answer as it streams. `scrollTop` is written, never read during
  // render, so this costs no layout work on the token path.
  useEffect(() => {
    const log = logRef.current;
    if (log) log.scrollTop = log.scrollHeight;
  }, [turns]);

  const submit = (question: string) => {
    if (isStreaming) return;
    ask(question);
    setDraft("");
  };

  const remaining = MAX_MESSAGE_LENGTH - draft.length;
  const isEmpty = turns.length === 0;

  return (
    <section
      role="dialog"
      aria-labelledby={titleId}
      // The export's 80px/92px offsets, plus the safe-area inset so the panel
      // clears an iPhone's home indicator. env() is 0px everywhere else, so
      // this changes nothing on a device without one.
      className="fixed right-4 bottom-[calc(5rem+env(safe-area-inset-bottom))] z-30 flex w-[min(380px,calc(100vw-2rem))] flex-col overflow-hidden rounded-card border border-line bg-bg2 shadow-float md:right-6 md:bottom-[calc(5.75rem+env(safe-area-inset-bottom))]"
    >
      <header className="flex items-center justify-between gap-2 border-b border-line px-4.5 py-4">
        <h2
          id={titleId}
          className="min-w-0 truncate text-[15px] font-extrabold uppercase [font-stretch:115%]"
        >
          Ask about me
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close chat"
          className="flex size-9 shrink-0 cursor-pointer touch-manipulation items-center justify-center rounded-full border border-line bg-transparent text-fg transition-colors duration-150 hover:border-fg"
        >
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            aria-hidden="true"
          >
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </header>

      <div
        ref={logRef}
        // A log, not a live region shouting every token: `polite` on an answer
        // that updates thirty times a second would make a screen reader
        // unusable. The finished answer is announced once, by the status line
        // below, which is what someone listening actually needs.
        role="log"
        aria-label="Conversation"
        className="flex max-h-[min(55vh,420px)] flex-col gap-3 overflow-y-auto overscroll-contain p-4.5"
      >
        {isEmpty ? (
          <div className="flex flex-col gap-3">
            <p className="text-sm leading-relaxed text-mute">
              Ask me anything about {siteConfig.fullName}&rsquo;s work. I answer from
              this site&rsquo;s own pages and cite them.
            </p>
            <ul className="flex list-none flex-col gap-2">
              {SUGGESTIONS.map((suggestion) => (
                <li key={suggestion}>
                  <button
                    type="button"
                    onClick={() => submit(suggestion)}
                    className="w-full cursor-pointer touch-manipulation rounded-xs border border-line bg-transparent px-3 py-2 text-left font-mono text-chip text-fg transition-colors duration-150 hover:border-fg"
                  >
                    {suggestion}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          turns.map((turn, index) => (
            <div
              // Turns are only ever appended and the last one patched in place,
              // so the index is stable for the lifetime of a conversation.
              key={index}
              className={cn(
                "max-w-[85%] rounded-bubble px-3.5 py-3 text-sm leading-relaxed break-words",
                turn.role === "user"
                  ? "grad self-end rounded-br-[4px] text-white"
                  : "self-start rounded-bl-[4px] border border-line bg-bg",
              )}
            >
              {/* Plain text, never HTML: the answer comes from a language model
                  reading site content, and nothing in that path is trusted
                  enough to render as markup. */}
              <p className="whitespace-pre-wrap">{turn.content}</p>
              {turn.sources ? <SourceChips sources={turn.sources} /> : null}
            </div>
          ))
        )}

        {isThinking ? (
          <p className="self-start font-mono text-chip text-mute" aria-hidden="true">
            Thinking…
          </p>
        ) : null}

        {error ? (
          <p role="alert" className="self-start text-sm leading-relaxed text-err-text">
            {error.message}
            {error.retryAfterSeconds
              ? ` Try again in ${formatWait(error.retryAfterSeconds)}.`
              : null}
          </p>
        ) : null}
      </div>

      {/* Announced once per answer, so a screen reader hears "answer complete"
          rather than every token as it lands. */}
      <VisuallyHidden aria-live="polite">
        {isStreaming ? "" : lastAnswerSummary(turns)}
      </VisuallyHidden>

      <p className="px-4.5 pb-3 font-mono text-[11px] text-mute">{DISCLAIMER}</p>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          submit(draft);
        }}
        className="flex gap-2 border-t border-line p-3"
      >
        <label htmlFor={inputId} className="sr-only">
          Ask a question
        </label>
        <input
          ref={inputRef}
          id={inputId}
          name="question"
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          maxLength={MAX_MESSAGE_LENGTH}
          placeholder="Ask anything about my work…"
          autoComplete="off"
          spellCheck={false}
          enterKeyHint="send"
          aria-describedby={`${inputId}-count`}
          className="min-h-11 min-w-0 flex-1 rounded-full border border-line bg-bg px-4 text-[15px] font-medium text-fg outline-none placeholder:text-mute focus:border-ac1"
        />
        {isStreaming ? (
          <button
            type="button"
            onClick={stop}
            aria-label="Stop answering"
            className="grad flex size-11 shrink-0 cursor-pointer touch-manipulation items-center justify-center rounded-full border-0 text-white"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <rect x="6" y="6" width="12" height="12" rx="2" />
            </svg>
          </button>
        ) : (
          <button
            type="submit"
            disabled={draft.trim().length === 0}
            aria-label="Send question"
            className="grad flex size-11 shrink-0 cursor-pointer touch-manipulation items-center justify-center rounded-full border-0 text-white disabled:cursor-not-allowed disabled:opacity-60"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </button>
        )}
      </form>

      {/* Only announced near the limit. A counter that reads out on every
          keystroke is noise for the 990 characters nobody is near. */}
      <VisuallyHidden id={`${inputId}-count`} aria-live="polite">
        {remaining <= 100 ? `${remaining} characters left` : ""}
      </VisuallyHidden>
    </section>
  );
}

/** "45 seconds" / "2 minutes", for the 429 message. */
function formatWait(seconds: number): string {
  if (seconds < 60) return `${seconds} second${seconds === 1 ? "" : "s"}`;
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} minute${minutes === 1 ? "" : "s"}`;
}

/** The finished answer, for the one polite announcement per turn. */
function lastAnswerSummary(turns: { role: string; content: string }[]): string {
  const last = turns.at(-1);
  return last && last.role === "assistant" && last.content ? last.content : "";
}
