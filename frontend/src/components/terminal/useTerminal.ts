"use client";

import { useCallback, useState } from "react";

import {
  complete,
  run,
  welcomeLines,
  type TerminalEffect,
  type TerminalLine,
} from "@/components/terminal/commands";
import { findCommand, type Command, type CommandData } from "@/lib/commands/registry";

import type { CommandContext } from "@/lib/commands/context";

/**
 * The terminal's state: what is on screen, what is being typed, where the
 * prompt points, and the history behind it.
 *
 * Everything the language decides lives in `commands.ts`; this hook is the part
 * that cannot be pure — it keeps the lines, walks the history and hands each
 * command's effect to the `CommandContext`.
 */

type UseTerminalOptions = {
  /** The registry, so an effect can be resolved to the command that runs it. */
  commands: readonly Command[];
  context: CommandContext;
  /** Pages and projects, for `ls`, `cd`, `cat`, `open` and completion. */
  data: CommandData;
  onClose: () => void;
};

export type Terminal = {
  lines: TerminalLine[];
  input: string;
  cwd: string;
  onInputChange: (value: string) => void;
  onKeyDown: (event: React.KeyboardEvent<HTMLInputElement>) => void;
};

export function useTerminal({
  commands,
  context,
  data,
  onClose,
}: UseTerminalOptions): Terminal {
  const [lines, setLines] = useState<TerminalLine[]>(welcomeLines);
  const [input, setInput] = useState("");
  const [cwd, setCwd] = useState("");
  const [history, setHistory] = useState<string[]>([]);
  // null means "typing a new line"; a number is a position in `history`.
  const [historyIndex, setHistoryIndex] = useState<number | null>(null);

  const apply = useCallback(
    (effect: TerminalEffect) => {
      if (effect.type === "close") {
        onClose();
        return;
      }

      if (effect.type === "theme") {
        context.setTheme(effect.theme);
        return;
      }

      // The registry is the only place that knows what a destination does, so
      // the terminal asks it rather than calling the router itself.
      findCommand(commands, effect.commandId)?.run(context);
    },
    [commands, context, onClose],
  );

  const submit = useCallback(() => {
    const typed = input;
    const result = run({ input: typed, cwd, data });

    setLines((current) =>
      result.clear
        ? []
        : [
            ...current,
            { kind: "command", text: typed, cwd },
            ...result.lines,
            // The export leaves a blank line after every command, which is
            // what keeps a wall of output readable.
            ...(result.lines.length > 0
              ? [{ kind: "output" as const, text: "", tone: "plain" as const }]
              : []),
          ],
    );

    setCwd(result.cwd);
    setInput("");
    setHistoryIndex(null);
    if (typed.trim()) setHistory((current) => [...current, typed.trim()]);
    if (result.effect) apply(result.effect);
  }, [apply, cwd, data, input]);

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (event.key === "Enter") {
        event.preventDefault();
        submit();
        return;
      }

      if (event.key === "Tab" && !event.shiftKey) {
        // Tab completes rather than moving focus. `stopPropagation` as well as
        // `preventDefault`: the dialog's focus trap moves focus from its own
        // keydown handler further up the tree, and would do it regardless of a
        // prevented default.
        //
        // Shift+Tab is deliberately left alone — it is the only way to reach
        // the close button from the prompt with a keyboard, and no shell does
        // anything with it.
        event.preventDefault();
        event.stopPropagation();
        setInput((current) => complete(current, data));
        return;
      }

      if (event.key === "ArrowUp") {
        event.preventDefault();
        if (history.length === 0) return;

        const next =
          historyIndex === null
            ? history.length - 1
            : Math.max(0, historyIndex - 1);

        setHistoryIndex(next);
        setInput(history[next]);
        return;
      }

      if (event.key === "ArrowDown") {
        event.preventDefault();
        if (historyIndex === null) return;

        const next = historyIndex + 1;

        // Walking past the newest entry returns to the empty line being
        // typed, rather than sticking on the last command.
        if (next >= history.length) {
          setHistoryIndex(null);
          setInput("");
          return;
        }

        setHistoryIndex(next);
        setInput(history[next]);
      }
    },
    [data, history, historyIndex, submit],
  );

  return { lines, input, cwd, onInputChange: setInput, onKeyDown };
}
