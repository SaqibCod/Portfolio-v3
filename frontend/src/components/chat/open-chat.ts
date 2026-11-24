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

/** Asks the launcher to open the chat panel. No-op during server rendering. */
export function requestOpenChat(): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(OPEN_CHAT_EVENT));
}
