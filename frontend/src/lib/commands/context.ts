/**
 * Everything a command is allowed to do to the running app.
 *
 * The registry is a plain data module — it has to be, because the palette and
 * the terminal both read it and neither one may drag React, the router or the
 * theme store into the other's chunk. So a command never reaches for a hook;
 * it is handed this object and calls one of its members. The two overlays each
 * build one from their own client component, and a test builds one out of
 * `vi.fn()`s, which is why every command in the registry is testable without
 * rendering anything.
 */
export type ThemeChoice = "dark" | "light";

export type CommandContext = {
  /** Client-side navigation to a route on this site. */
  navigate(href: string): void;
  /** A link off this site. Opens in a new tab, as the footer's links do. */
  openExternal(href: string): void;
  setTheme(theme: ThemeChoice): void;
  /**
   * Opens the chat panel, optionally with the question already typed into the
   * composer. Nothing is sent until the visitor presses Enter.
   */
  openChat(question?: string): void;
  openTerminal(): void;
  closeTerminal(): void;
  /** Writes to the clipboard and reports the outcome through {@link toast}. */
  copy(text: string, label: string): void;
  /** A short confirmation for an action with no other visible outcome. */
  toast(message: string): void;
};
