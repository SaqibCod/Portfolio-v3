import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { PaletteHint } from "@/components/palette/PaletteHint";
import { OPEN_PALETTE_EVENT } from "@/components/palette/open-palette";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("PaletteHint", () => {
  it("offers two ways in, both named the same thing", () => {
    render(<PaletteHint />);

    // The pill above 760px and the icon below it. Both are in the markup —
    // which one shows is a media query, not a branch.
    expect(
      screen.getAllByRole("button", { name: "Open command palette" }),
    ).toHaveLength(2);
  });

  it("asks the launcher to open the palette", async () => {
    const user = userEvent.setup();
    const opened = vi.fn();
    window.addEventListener(OPEN_PALETTE_EVENT, opened);
    render(<PaletteHint />);

    await user.click(
      screen.getAllByRole("button", { name: "Open command palette" })[0],
    );

    expect(opened).toHaveBeenCalledTimes(1);
    window.removeEventListener(OPEN_PALETTE_EVENT, opened);
  });

  it("names the shortcut the way this keyboard has it", () => {
    vi.stubGlobal("navigator", { platform: "Win32", userAgent: "" });
    render(<PaletteHint />);

    // A shortcut nobody is told about does not exist, and telling a Windows
    // visitor about ⌘ tells them nothing.
    expect(screen.getByText("Ctrl K")).toBeInTheDocument();
  });

  it("names it ⌘K on an Apple keyboard", () => {
    vi.stubGlobal("navigator", { platform: "MacIntel", userAgent: "" });
    render(<PaletteHint />);

    expect(screen.getByText("⌘K")).toBeInTheDocument();
  });

  it("also reports the shortcut to assistive technology", () => {
    render(<PaletteHint />);

    expect(
      screen.getAllByRole("button", { name: "Open command palette" })[0],
    ).toHaveAttribute("aria-keyshortcuts", "Meta+K Control+K");
  });
});
