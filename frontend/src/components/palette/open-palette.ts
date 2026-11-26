/**
 * The channel from the header's search button to the launcher that owns the
 * palette, mirroring `chat/open-chat.ts` and for the same reason: the overlay
 * lives at the bottom of the layout, the button that opens it lives in the
 * header, and neither should become the other's parent.
 */
export const OPEN_PALETTE_EVENT = "portfolio:open-palette";

/** Asks the launcher to open the palette. No-op during server rendering. */
export function requestOpenPalette(): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(OPEN_PALETTE_EVENT));
}
