"use client";

import { requestOpenChat } from "@/components/chat/open-chat";
import { Button } from "@/components/ui/Button";

type AskAiButtonProps = {
  className?: string;
};

/**
 * The export's "Ask the AI about me" button in the About section.
 *
 * Its own tiny client component so `About` stays a server component: the home
 * page's text is the most SEO-critical content on the site, and none of it
 * should depend on JavaScript because one button needs a click handler.
 *
 * It opens the chat through a window event rather than holding the panel's
 * state, so there is exactly one panel on the page no matter how many places
 * offer to open it.
 */
export function AskAiButton({ className }: AskAiButtonProps) {
  return (
    <Button
      variant="primary"
      // Wrapped rather than passed directly: `requestOpenChat` takes an
      // optional question, and React would hand it the click event.
      onClick={() => requestOpenChat()}
      className={className}
    >
      Ask the AI about me
    </Button>
  );
}
