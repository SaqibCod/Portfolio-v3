import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { CommandLauncher } from "@/components/palette/CommandLauncher";
import { requestOpenPalette } from "@/components/palette/open-palette";
import { commandPages, type CommandData } from "@/lib/commands/registry";

const push = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("next-themes", () => ({
  useTheme: () => ({ resolvedTheme: "dark", setTheme: vi.fn() }),
}));

// Both overlays are replaced with markers: these tests are about the
// launcher's own job — which overlay is mounted and what opens it — and each
// overlay's behaviour is covered against the real thing in its own file. The
// factory is told apart by the import it was given, since `next/dynamic` is
// called once per overlay.
vi.mock("next/dynamic", () => ({
  default: (loader: () => Promise<unknown>) => {
    const isTerminal = loader.toString().includes("TerminalOverlay");

    return function MockOverlay({
      onClose,
      context,
    }: {
      onClose: () => void;
      context: { openTerminal: () => void };
    }) {
      const name = isTerminal ? "terminal" : "palette";

      return (
        <div data-testid={name}>
          <input aria-label={`${name} field`} />
          <button type="button" onClick={onClose}>
            close from {name}
          </button>
          {isTerminal ? null : (
            <button type="button" onClick={() => context.openTerminal()}>
              open terminal
            </button>
          )}
        </div>
      );
    };
  },
}));

const data: CommandData = { pages: commandPages(true), projects: [] };

beforeEach(() => {
  push.mockReset();
  // No idle callback in jsdom, and the timeout fallback would preload the
  // chunk mid-test.
  vi.stubGlobal("requestIdleCallback", undefined);
  vi.stubGlobal("cancelIdleCallback", undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("CommandLauncher", () => {
  it("ships nothing but a live region until something opens", () => {
    render(<CommandLauncher data={data} />);

    expect(screen.queryByTestId("palette")).not.toBeInTheDocument();
    // The toast region has to be in the document before it has anything to
    // say, or a screen reader never announces the first message.
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("opens on Cmd+K and again on Ctrl+K", async () => {
    const user = userEvent.setup();
    render(<CommandLauncher data={data} />);

    await user.keyboard("{Meta>}k{/Meta}");
    expect(screen.getByTestId("palette")).toBeInTheDocument();

    await user.keyboard("{Meta>}k{/Meta}");
    expect(screen.queryByTestId("palette")).not.toBeInTheDocument();

    await user.keyboard("{Control>}k{/Control}");
    expect(screen.getByTestId("palette")).toBeInTheDocument();
  });

  it("leaves the shortcut alone while a field has focus", async () => {
    const user = userEvent.setup();
    render(
      <>
        <input aria-label="message" />
        <CommandLauncher data={data} />
      </>,
    );

    await user.click(screen.getByRole("textbox", { name: "message" }));
    await user.keyboard("{Control>}k{/Control}");

    // Ctrl+K deletes to end of line in a text field on more than one
    // platform; the contact form is the last place to steal it from.
    expect(screen.queryByTestId("palette")).not.toBeInTheDocument();
  });

  it("still closes with the shortcut from inside its own field", async () => {
    const user = userEvent.setup();
    render(<CommandLauncher data={data} />);

    await user.keyboard("{Meta>}k{/Meta}");
    await user.click(screen.getByRole("textbox", { name: "palette field" }));
    await user.keyboard("{Meta>}k{/Meta}");

    expect(screen.queryByTestId("palette")).not.toBeInTheDocument();
  });

  it("opens when the header button asks it to", () => {
    render(<CommandLauncher data={data} />);

    // A bare DOM event rather than an interaction, so nothing else flushes
    // the state update it causes.
    act(() => requestOpenPalette());

    expect(screen.getByTestId("palette")).toBeInTheDocument();
  });

  it("closes when the palette asks to be closed", async () => {
    const user = userEvent.setup();
    render(<CommandLauncher data={data} />);

    act(() => requestOpenPalette());
    await user.click(screen.getByRole("button", { name: "close from palette" }));

    expect(screen.queryByTestId("palette")).not.toBeInTheDocument();
  });

  it("hands over from the palette to the terminal, one at a time", async () => {
    const user = userEvent.setup();
    render(<CommandLauncher data={data} />);

    act(() => requestOpenPalette());
    await user.click(screen.getByRole("button", { name: "open terminal" }));

    expect(screen.getByTestId("terminal")).toBeInTheDocument();
    // Two overlays over each other would mean two focus traps fighting.
    expect(screen.queryByTestId("palette")).not.toBeInTheDocument();
  });

  it("ignores k without a modifier", async () => {
    const user = userEvent.setup();
    render(<CommandLauncher data={data} />);

    await user.keyboard("k");

    expect(screen.queryByTestId("palette")).not.toBeInTheDocument();
  });
});
