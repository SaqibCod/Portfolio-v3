"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { useId, useRef } from "react";

import {
  TERMINAL_BANNER,
  TERMINAL_USER,
  type LineTone,
} from "@/components/terminal/commands";
import { useTerminal } from "@/components/terminal/useTerminal";
import { Kbd } from "@/components/ui/Kbd";

import type { CommandContext } from "@/lib/commands/context";
import type { Command, CommandData } from "@/lib/commands/registry";

/**
 * `.term` from the palette export: the site as a prompt.
 *
 * Offered only to visitors with a real keyboard — the palette's "Open terminal"
 * row is absent on a touch device — and it shows nothing the pages do not. Each
 * command either prints what a page already says or navigates to it, which is
 * what keeps this an interface onto the site rather than a second copy of it.
 *
 * A Radix dialog, like the palette, for the focus trap, the scroll lock,
 * Escape and returning focus to whatever opened it. `exit` closes it too.
 *
 * Loaded in its own chunk, only once it is asked for. See `CommandLauncher`.
 */

const TONE_CLASS: Record<LineTone, string> = {
  plain: "",
  dim: "text-tdim",
  accent: "font-semibold text-tprompt",
  error: "text-terr",
  ok: "text-tok",
};

type TerminalOverlayProps = {
  commands: readonly Command[];
  context: CommandContext;
  data: CommandData;
  onClose: () => void;
};

export function TerminalOverlay({
  commands,
  context,
  data,
  onClose,
}: TerminalOverlayProps) {
  const { lines, input, cwd, onInputChange, onKeyDown } = useTerminal({
    commands,
    context,
    data,
    onClose,
  });

  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <Dialog.Root
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-scrim backdrop-blur-[6px]" />
        <Dialog.Content
          aria-label="Terminal"
          // Radix looks for a description and warns when there is none; the
          // hint bar at the bottom is the description, and it is visible.
          aria-describedby={undefined}
          // The prompt, not the close button, is what someone opening a
          // terminal wants focus in — and Radix would otherwise take the first
          // focusable element, which is the close button in the title bar.
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            inputRef.current?.focus();
          }}
          className="fixed inset-7 z-40 flex flex-col overflow-hidden rounded-lg border border-line bg-tbg font-mono text-tfg shadow-term"
        >
          <div className="flex min-h-13 shrink-0 items-center gap-3 border-b border-line pr-3.5 pl-4.5">
            <div aria-hidden="true" className="flex gap-[7px]">
              <i className="size-[11px] rounded-full bg-err" />
              <i className="size-[11px] rounded-full bg-warn" />
              <i className="size-[11px] rounded-full bg-ok" />
            </div>
            <p className="flex-1 truncate text-center text-[13px] text-tdim">
              {TERMINAL_USER}: ~{cwd}
            </p>
            <button
              type="button"
              onClick={onClose}
              className="inline-flex min-h-9 shrink-0 cursor-pointer items-center gap-2 rounded-full border border-line bg-transparent pr-1.5 pl-3 font-mono text-[12px] font-medium text-tdim transition-colors duration-150 hover:border-tfg hover:text-tfg"
            >
              close <Kbd>esc</Kbd>
            </button>
          </div>

          <div
            // A log rather than a live region that re-reads the screen: each
            // command's output is appended once and announced once.
            role="log"
            aria-live="polite"
            aria-label="Terminal output"
            // Command names, slugs and routes are identifiers, not prose. An
            // auto-translating browser that turns `ls` into a verb leaves the
            // visitor reading output for a terminal that does not exist.
            translate="no"
            className="flex-1 overflow-y-auto overscroll-contain px-6.5 pt-5.5 pb-2 text-[15px] leading-[1.7]"
          >
            <p
              aria-hidden="true"
              className="display grad-text mb-3.5 font-sans text-[clamp(36px,8vw,64px)]"
            >
              {TERMINAL_BANNER}
            </p>

            {lines.map((line, index) =>
              line.kind === "command" ? (
                <div
                  // Lines are append-only and never reordered, so the index is
                  // a stable identity for one.
                  key={index}
                  className="min-h-[1.7em] break-words whitespace-pre-wrap"
                >
                  <span className="text-tprompt">{TERMINAL_USER}</span>:
                  <span className="text-tpath">~{line.cwd}</span>${" "}
                  {line.text}
                </div>
              ) : (
                <div
                  key={index}
                  className={`min-h-[1.7em] break-words whitespace-pre-wrap ${TONE_CLASS[line.tone]}`}
                >
                  {line.text}
                </div>
              ),
            )}
          </div>

          <div className="flex shrink-0 items-center gap-2.5 px-6.5 pt-2.5 pb-5.5 text-[15px]">
            <label htmlFor={inputId} translate="no" className="shrink-0">
              <span className="text-tprompt">{TERMINAL_USER}</span>:
              <span className="text-tpath">~{cwd}</span>$
            </label>
            <input
              ref={inputRef}
              id={inputId}
              type="text"
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
              aria-label="Terminal command"
              value={input}
              onChange={(event) => onInputChange(event.target.value)}
              onKeyDown={onKeyDown}
              className="min-w-0 flex-1 border-0 bg-transparent py-1.5 font-mono text-[15px] font-medium text-tfg caret-ac1 outline-none"
            />
          </div>

          <div className="flex shrink-0 flex-wrap gap-4.5 border-t border-line px-6.5 py-2.5 text-[12px] text-tdim">
            <span className="inline-flex items-center gap-1.5">
              <Kbd>tab</Kbd> complete
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Kbd>&#8593;</Kbd>
              <Kbd>&#8595;</Kbd> history
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Kbd>esc</Kbd> or exit to close
            </span>
            <span>type help for commands</span>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
