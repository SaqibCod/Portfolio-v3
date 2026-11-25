import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ChatLauncher } from "@/components/chat/ChatLauncher";
import { requestOpenChat } from "@/components/chat/open-chat";

// next/dynamic resolves asynchronously, which would make every assertion below
// race the chunk. The panel is replaced with a marker so these tests are about
// the launcher's own job: when the panel is mounted, and where focus goes.
vi.mock("next/dynamic", () => ({
  default: () => function MockChatPanel({ onClose }: { onClose: () => void }) {
    return (
      <div data-testid="chat-panel">
        <button type="button" onClick={onClose}>
          close from panel
        </button>
      </div>
    );
  },
}));

beforeEach(() => {
  vi.stubGlobal("requestIdleCallback", undefined);
  vi.stubGlobal("cancelIdleCallback", undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ChatLauncher", () => {
  it("renders only the launcher before anything is opened", () => {
    render(<ChatLauncher />);

    // The whole point of the component: on first paint the chat is one button.
    // Everything else arrives in a chunk fetched while the browser is idle.
    expect(screen.getByRole("button", { name: /ask about me/i })).toBeInTheDocument();
    expect(screen.queryByTestId("chat-panel")).not.toBeInTheDocument();
  });

  it("opens the panel on click", async () => {
    render(<ChatLauncher />);

    await userEvent.click(screen.getByRole("button", { name: /ask about me/i }));

    expect(screen.getByTestId("chat-panel")).toBeInTheDocument();
  });

  it("reports its state to assistive technology", async () => {
    render(<ChatLauncher />);
    const launcher = screen.getByRole("button", { name: /ask about me/i });

    expect(launcher).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(launcher);

    expect(screen.getByRole("button", { name: /close chat/i })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
  });

  it("closes again on a second click", async () => {
    render(<ChatLauncher />);

    await userEvent.click(screen.getByRole("button", { name: /ask about me/i }));
    await userEvent.click(screen.getByRole("button", { name: /close chat/i }));

    expect(screen.queryByTestId("chat-panel")).not.toBeInTheDocument();
  });

  it("returns focus to the launcher when the panel closes itself", async () => {
    render(<ChatLauncher />);

    await userEvent.click(screen.getByRole("button", { name: /ask about me/i }));
    await userEvent.click(screen.getByRole("button", { name: "close from panel" }));

    // Without this a keyboard visitor who closes the panel is dropped at the
    // top of the document and has to tab through the whole page to get back.
    expect(screen.getByRole("button", { name: /ask about me/i })).toHaveFocus();
  });

  it("opens when another part of the page asks it to", () => {
    render(<ChatLauncher />);

    // How the About section's "Ask the AI about me" button reaches the panel
    // without that section having to become a client component. Wrapped in
    // act() because this is a bare DOM event rather than a user interaction,
    // so nothing else flushes the state update it causes.
    act(() => requestOpenChat());

    expect(screen.getByTestId("chat-panel")).toBeInTheDocument();
  });
});
