"use client";

import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
} from "react";

import { requestOpenChat } from "@/components/chat/open-chat";
import { OPEN_PALETTE_EVENT } from "@/components/palette/open-palette";
import { Toast } from "@/components/ui/Toast";
import { buildCommands, type CommandData } from "@/lib/commands/registry";

import type { CommandContext, ThemeChoice } from "@/lib/commands/context";

/**
 * The palette, in its own chunk. Nothing of cmdk or Radix Dialog reaches the
 * first paint — what ships with the page is this component: a keyboard
 * listener, a media query and a `useState`.
 */
const CommandPalette = dynamic(
  () =>
    import("@/components/palette/CommandPalette").then(
      (module) => module.CommandPalette,
    ),
  { ssr: false },
);

/**
 * The terminal, in a chunk of its own — not even fetched alongside the
 * palette, since most visitors who open the palette never ask for a prompt.
 */
const TerminalOverlay = dynamic(
  () =>
    import("@/components/terminal/TerminalOverlay").then(
      (module) => module.TerminalOverlay,
    ),
  { ssr: false },
);

/** Which overlay is up. Only ever one, and most of the time none. */
type Mode = "closed" | "palette" | "terminal";

/**
 * A device with no hover and a coarse pointer — a phone or a tablet.
 *
 * The terminal is hidden there, which is the one behavioural difference
 * between the two kinds of visitor: a terminal needs Tab, arrow keys and a
 * prompt, and an on-screen keyboard has none of them to spare.
 */
const touchQuery = "(hover: none) and (pointer: coarse)";

function subscribeToTouch(onChange: () => void) {
  const media = window.matchMedia(touchQuery);
  media.addEventListener("change", onChange);
  return () => media.removeEventListener("change", onChange);
}

function useIsTouchOnly(): boolean {
  return useSyncExternalStore(
    subscribeToTouch,
    () => window.matchMedia(touchQuery).matches,
    // Server-rendered as false. Nothing is rendered from it either way — the
    // overlays are client-only — so there is no markup to mismatch.
    () => false,
  );
}

/**
 * Whether a keystroke belongs to whatever the visitor is typing in.
 *
 * Ctrl+K is "kill to end of line" in a text field on more than one platform,
 * and the contact form is the last place to steal a shortcut from. So the
 * shortcut yields while a field has focus — unless an overlay is already open,
 * where the same keystroke is the way back out.
 */
function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  return ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}

type CommandLauncherProps = {
  /**
   * The registry's data half, built on the server: `/status` is only a page
   * while something is monitored, and the project summaries come from MDX that
   * must not be imported into a browser chunk.
   */
  data: CommandData;
};

/**
 * Owns both overlays, the shortcut that opens them, and the one `CommandContext`
 * every command runs against.
 *
 * Mounted once in the root layout, next to the chat launcher and for the same
 * reasons: one palette per document however many buttons offer it, and page
 * chrome belongs after the footer rather than inside `<main>`.
 */
export function CommandLauncher({ data }: CommandLauncherProps) {
  const [mode, setMode] = useState<Mode>("closed");
  const [toast, setToast] = useState<string | null>(null);
  const router = useRouter();
  const { resolvedTheme, setTheme } = useTheme();
  const touch = useIsTouchOnly();

  const close = useCallback(() => setMode("closed"), []);
  const clearToast = useCallback(() => setToast(null), []);

  // Unknown during server rendering and the first client render; dark is the
  // design's default and the label is corrected as soon as next-themes reports.
  const theme: ThemeChoice = resolvedTheme === "light" ? "light" : "dark";

  const context = useMemo<CommandContext>(
    () => ({
      navigate: (href) => {
        router.push(href);
        setToast(`Opening ${href}`);
      },
      openExternal: (href) => {
        window.open(href, "_blank", "noopener,noreferrer");
      },
      setTheme,
      openChat: (question) => requestOpenChat(question),
      openTerminal: () => setMode("terminal"),
      closeTerminal: () => setMode("closed"),
      copy: (text, label) => {
        // Clipboard access can be refused — an insecure origin, or a browser
        // that wants a user gesture it did not see. Saying so beats a
        // confirmation for something that did not happen.
        navigator.clipboard
          ?.writeText(text)
          .then(() => setToast(label))
          .catch(() => setToast(`Could not copy — ${text}`));
      },
      toast: (message) => setToast(message),
    }),
    [router, setTheme],
  );

  const commands = useMemo(
    () => buildCommands(data, { theme, touch }),
    [data, theme, touch],
  );

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() !== "k") return;
      if (!event.metaKey && !event.ctrlKey) return;
      if (mode === "closed" && isTypingTarget(event.target)) return;

      event.preventDefault();
      setMode(mode === "closed" ? "palette" : "closed");
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [mode]);

  useEffect(() => {
    const open = () => setMode("palette");

    window.addEventListener(OPEN_PALETTE_EVENT, open);
    return () => window.removeEventListener(OPEN_PALETTE_EVENT, open);
  }, []);

  // Warms the palette's chunk once the browser is idle, so the first Cmd+K
  // renders instead of waiting on a round trip. Never during the first paint.
  useEffect(() => {
    const preload = () => {
      void import("@/components/palette/CommandPalette");
    };

    if (typeof window.requestIdleCallback === "function") {
      const handle = window.requestIdleCallback(preload);
      return () => window.cancelIdleCallback(handle);
    }

    const timer = window.setTimeout(preload, 2500);
    return () => window.clearTimeout(timer);
  }, []);

  return (
    <>
      {mode === "palette" ? (
        <CommandPalette
          commands={commands}
          context={context}
          touch={touch}
          onClose={close}
        />
      ) : null}

      {mode === "terminal" ? (
        <TerminalOverlay
          commands={commands}
          context={context}
          data={data}
          onClose={close}
        />
      ) : null}

      <Toast message={toast} onExpire={clearToast} />
    </>
  );
}
