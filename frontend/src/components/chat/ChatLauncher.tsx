"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useRef, useState } from "react";

import { OPEN_CHAT_EVENT, type OpenChatEvent } from "@/components/chat/open-chat";

/**
 * The panel, in its own chunk, fetched only when it is needed.
 *
 * `ssr: false` because there is nothing to prerender: the panel is empty until
 * someone asks something, and every answer it shows restates content that is
 * already server-rendered on the page it cites. Rendering it on the server
 * would add markup for a crawler to read that says nothing new.
 */
const ChatPanel = dynamic(
  () => import("@/components/chat/ChatPanel").then((module) => module.ChatPanel),
  { ssr: false },
);

/**
 * `.fab` from the home export: the floating "Ask about me" pill, and the owner
 * of the panel's open state.
 *
 * This is the only part of the chat that ships with the page, and it is a
 * button and an icon. Everything else — the panel, the hook, the SSE parser —
 * arrives in a separate chunk that is fetched while the browser is idle, so the
 * feature costs the first paint nothing and is still instant on click. That is
 * the phase's "chat code is not in the initial page bundle" criterion, and it
 * is the reason this component is deliberately almost empty.
 *
 * It also listens for {@link OPEN_CHAT_EVENT}, which is how the "Ask the AI
 * about me" button in the About section opens the panel without that section
 * having to become a client component.
 */
export function ChatLauncher() {
  // The question travels with the open state so one `setState` does both:
  // opening with a question and opening without one are the same transition.
  const [chat, setChat] = useState<{ isOpen: boolean; question?: string }>({
    isOpen: false,
  });
  const isOpen = chat.isOpen;
  const launcherRef = useRef<HTMLButtonElement>(null);

  const open = useCallback(
    (question?: string) => setChat({ isOpen: true, question }),
    [],
  );

  const close = useCallback(() => {
    setChat({ isOpen: false });
    // Focus goes back where it came from. Without this, closing the panel
    // drops focus to the top of the document and a keyboard visitor has to
    // tab through the whole page to get back.
    launcherRef.current?.focus();
  }, []);

  // Warms the panel's chunk once the browser has nothing better to do, so the
  // first click renders rather than waits on a network round trip. Idle rather
  // than on mount: this must never compete with the hero image for bandwidth.
  useEffect(() => {
    const preload = () => {
      void import("@/components/chat/ChatPanel");
    };

    // requestIdleCallback is not everywhere yet; a timeout is a fine stand-in,
    // since the only thing that matters is "not during the initial paint".
    if (typeof window.requestIdleCallback === "function") {
      const handle = window.requestIdleCallback(preload);
      return () => window.cancelIdleCallback(handle);
    }

    const timer = window.setTimeout(preload, 2500);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    const onRequest = (event: Event) => {
      open((event as OpenChatEvent).detail?.question);
    };

    window.addEventListener(OPEN_CHAT_EVENT, onRequest);
    return () => window.removeEventListener(OPEN_CHAT_EVENT, onRequest);
  }, [open]);

  return (
    <>
      {isOpen ? (
        <ChatPanel onClose={close} initialQuestion={chat.question} />
      ) : null}

      <button
        ref={launcherRef}
        type="button"
        onClick={() => (isOpen ? close() : open())}
        aria-expanded={isOpen}
        // The export's 24px offset, plus the safe-area inset: without it the
        // pill sits under an iPhone's home indicator, which is both hard to
        // press and the one control that opens this whole feature.
        className="grad-float fixed right-4 bottom-[calc(1rem+env(safe-area-inset-bottom))] z-30 inline-flex min-h-13 cursor-pointer touch-manipulation items-center gap-2.5 rounded-full border-0 pr-5.5 pl-4.5 text-btn font-bold tracking-[0.04em] text-white uppercase md:right-6 md:bottom-[calc(1.5rem+env(safe-area-inset-bottom))]"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinejoin="round"
          aria-hidden="true"
          className="shrink-0"
        >
          <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z" />
          <path d="M19 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z" />
        </svg>
        {isOpen ? "Close chat" : "Ask about me"}
      </button>
    </>
  );
}
