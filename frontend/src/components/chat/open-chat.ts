/**
 * The one-way channel from "open the chat" buttons elsewhere on the page to the
 * launcher that owns the panel.
 *
 * A DOM event rather than React context, because the alternative costs the site
 * something real: a provider wrapping the tree would make every page that
 * renders an open-chat button a client component, and those pages — the home
 * page above all — are the ones whose content has to be server-rendered HTML.
 * An event keeps `About` a server component with one small client button inside
 * it, and keeps the launcher the only thing that knows the panel exists.
 */
export const OPEN_CHAT_EVENT = "portfolio:open-chat";

/** What the event carries: the question to start from, if the caller has one. */
export type OpenChatDetail = { question?: string };

export type OpenChatEvent = CustomEvent<OpenChatDetail>;

/**
 * Asks the launcher to open the chat panel. No-op during server rendering.
 *
 * The optional question is typed into the composer and left there — the palette
 * offers "Ask the AI about “…”" for a search that matched nothing, and sending
 * it unread would turn a mistyped search into a request. The visitor still
 * presses Enter.
 */
export function requestOpenChat(question?: string): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(
    new CustomEvent<OpenChatDetail>(OPEN_CHAT_EVENT, { detail: { question } }),
  );
}
