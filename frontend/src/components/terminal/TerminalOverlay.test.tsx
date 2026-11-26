import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { TerminalOverlay } from "@/components/terminal/TerminalOverlay";
import {
  buildCommands,
  commandPages,
  commandProjects,
  type CommandData,
} from "@/lib/commands/registry";

import type { CommandContext } from "@/lib/commands/context";

function makeContext(): CommandContext {
  return {
    navigate: vi.fn(),
    openExternal: vi.fn(),
    setTheme: vi.fn(),
    openChat: vi.fn(),
    openTerminal: vi.fn(),
    closeTerminal: vi.fn(),
    copy: vi.fn(),
    toast: vi.fn(),
  };
}

const data: CommandData = {
  pages: commandPages(true),
  projects: commandProjects([
    {
      slug: "githmind",
      title: "githMind",
      summary: "Chat with any GitHub repository.",
      stack: ["Next.js", "Spring Boot"],
      href: "/projects/githmind",
    },
  ]),
};

function renderTerminal() {
  const context = makeContext();
  const onClose = vi.fn();

  render(
    <TerminalOverlay
      commands={buildCommands(data, { theme: "dark", touch: false })}
      context={context}
      data={data}
      onClose={onClose}
    />,
  );

  return {
    context,
    onClose,
    field: screen.getByRole("textbox", { name: "Terminal command" }),
  };
}

describe("TerminalOverlay", () => {
  it("is a dialog with a log, and starts at the prompt", () => {
    const { field } = renderTerminal();

    expect(screen.getByRole("dialog", { name: "Terminal" })).toBeInTheDocument();
    // Output has to be announced as it appears, which is what the log role
    // plus a polite live region does.
    const log = screen.getByRole("log", { name: "Terminal output" });
    expect(log).toHaveAttribute("aria-live", "polite");
    // Focus starts in the field, not on the close button Radix would pick.
    expect(field).toHaveFocus();
  });

  it("echoes what was typed and prints the answer", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "cat githmind{Enter}");

    const log = screen.getByRole("log", { name: "Terminal output" });
    expect(log).toHaveTextContent("cat githmind");
    expect(log).toHaveTextContent("Chat with any GitHub repository.");
    expect(field).toHaveValue("");
  });

  it("navigates through the registry on cd and moves the prompt", async () => {
    const user = userEvent.setup();
    const { field, context } = renderTerminal();

    await user.type(field, "cd projects{Enter}");

    expect(context.navigate).toHaveBeenCalledWith("/projects");
    // Two of them: the title bar and the prompt label.
    expect(screen.getAllByText(/~\/projects/).length).toBeGreaterThan(0);
  });

  it("switches the theme", async () => {
    const user = userEvent.setup();
    const { field, context } = renderTerminal();

    await user.type(field, "theme light{Enter}");

    expect(context.setTheme).toHaveBeenCalledWith("light");
  });

  it("reports a command it does not have", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "sudo hire-me{Enter}");

    expect(
      screen.getByText("command not found: sudo. Try help"),
    ).toBeInTheDocument();
  });

  it("walks the history with the arrow keys", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "whoami{Enter}");
    await user.type(field, "contact{Enter}");

    await user.keyboard("{ArrowUp}");
    expect(field).toHaveValue("contact");

    await user.keyboard("{ArrowUp}");
    expect(field).toHaveValue("whoami");

    // Nothing older to reach; the oldest entry stays put.
    await user.keyboard("{ArrowUp}");
    expect(field).toHaveValue("whoami");

    await user.keyboard("{ArrowDown}");
    expect(field).toHaveValue("contact");

    // Walking past the newest entry returns to the empty line.
    await user.keyboard("{ArrowDown}");
    expect(field).toHaveValue("");
  });

  it("does not record a blank line in the history", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "whoami{Enter}");
    await user.keyboard("{Enter}");
    await user.keyboard("{ArrowUp}");

    expect(field).toHaveValue("whoami");
  });

  it("completes a command with Tab", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "wh");
    await user.keyboard("{Tab}");

    expect(field).toHaveValue("whoami ");
    // Tab completed instead of leaving the field, which takes stopping the
    // keystroke from reaching the dialog's focus trap.
    expect(field).toHaveFocus();
  });

  it("still lets Shift+Tab reach the close button", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "wh");
    await user.keyboard("{Shift>}{Tab}{/Shift}");

    // The one way out of the prompt by keyboard other than Escape, so the
    // completion key must not swallow it.
    expect(field).not.toHaveFocus();
    expect(field).toHaveValue("wh");
  });

  it("completes an argument with Tab", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "cd guest");
    await user.keyboard("{Tab}");

    expect(field).toHaveValue("cd guestbook");
  });

  it("clears the screen but keeps the prompt", async () => {
    const user = userEvent.setup();
    const { field } = renderTerminal();

    await user.type(field, "clear{Enter}");

    const log = screen.getByRole("log", { name: "Terminal output" });
    expect(log).not.toHaveTextContent("ls [pages|projects]");
    expect(field).toBeInTheDocument();
  });

  it("closes on exit", async () => {
    const user = userEvent.setup();
    const { field, onClose } = renderTerminal();

    await user.type(field, "exit{Enter}");

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes on Escape", async () => {
    const user = userEvent.setup();
    const { onClose } = renderTerminal();

    await user.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes on the title bar button", async () => {
    const user = userEvent.setup();
    const { onClose } = renderTerminal();

    await user.click(screen.getByRole("button", { name: /close/i }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
