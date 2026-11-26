import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { CommandPalette } from "@/components/palette/CommandPalette";
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
      stack: ["Next.js", "Spring Boot", "RAG"],
      href: "/projects/githmind",
    },
  ]),
};

function renderPalette(
  overrides: { touch?: boolean; context?: CommandContext } = {},
) {
  const context = overrides.context ?? makeContext();
  const onClose = vi.fn();

  render(
    <CommandPalette
      commands={buildCommands(data, {
        theme: "dark",
        touch: overrides.touch ?? false,
      })}
      context={context}
      touch={overrides.touch ?? false}
      onClose={onClose}
    />,
  );

  return { context, onClose };
}

describe("CommandPalette", () => {
  it("renders every group in the design's order", () => {
    renderPalette();

    // By cmdk's own attribute rather than by text: "Projects" is both a group
    // and a page, so matching on the words finds four things, not three.
    const headings = Array.from(
      document.querySelectorAll("[cmdk-group-heading]"),
    ).map((element) => element.textContent);

    expect(headings).toEqual(["Pages", "Projects", "Actions"]);
  });

  it("is a combobox over a labelled list of options", () => {
    renderPalette();

    // cmdk names the field from the dialog's label, so both carry the same
    // name; the placeholder is what says what can be typed.
    expect(
      screen.getByRole("combobox", { name: "Command palette" }),
    ).toHaveAttribute("placeholder", "Search pages, projects, actions…");
    expect(
      screen.getByRole("listbox", { name: "Pages, projects and actions" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("option").length).toBeGreaterThan(5);
  });

  it("runs the highlighted command on Enter and closes first", async () => {
    const user = userEvent.setup();
    const { context, onClose } = renderPalette();

    await user.keyboard("guestbook");
    await user.keyboard("{Enter}");

    expect(onClose).toHaveBeenCalled();
    expect(context.navigate).toHaveBeenCalledWith("/guestbook");
  });

  it("walks the list with the arrow keys", async () => {
    const user = userEvent.setup();
    const { context } = renderPalette();

    // Home is first and selected; one step down is the next page in the nav.
    await user.keyboard("{ArrowDown}{Enter}");

    expect(context.navigate).toHaveBeenCalledWith("/#about");
  });

  it("finds a command by a keyword that is not in its title", async () => {
    const user = userEvent.setup();
    const { context } = renderPalette();

    // "appearance" is the theme action's subtitle, not its name.
    await user.keyboard("appearance{Enter}");

    expect(context.setTheme).toHaveBeenCalledWith("light");
  });

  it("counts the results it is showing", async () => {
    const user = userEvent.setup();
    renderPalette();

    await user.keyboard("githmind");

    expect(screen.getByText("1 result")).toBeInTheDocument();
  });

  it("offers the chat when nothing matches, with the query", async () => {
    const user = userEvent.setup();
    const { context, onClose } = renderPalette();

    await user.keyboard("kubernetes");

    expect(screen.getByText(/no results for/i)).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /ask the ai about/i }),
    );

    expect(onClose).toHaveBeenCalled();
    expect(context.openChat).toHaveBeenCalledWith("kubernetes");
  });

  it("sends Enter to the chat when there is nothing to select", async () => {
    const user = userEvent.setup();
    const { context } = renderPalette();

    await user.keyboard("kubernetes{Enter}");

    expect(context.openChat).toHaveBeenCalledWith("kubernetes");
  });

  it("closes on Escape", async () => {
    const user = userEvent.setup();
    const { onClose } = renderPalette();

    await user.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalled();
  });

  it("offers the terminal on a pointer device and not on a touch one", () => {
    const { unmount } = render(
      <CommandPalette
        commands={buildCommands(data, { theme: "dark", touch: false })}
        context={makeContext()}
        touch={false}
        onClose={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("option", { name: /open terminal/i }),
    ).toBeInTheDocument();
    unmount();

    renderPalette({ touch: true });

    expect(
      screen.queryByRole("option", { name: /open terminal/i }),
    ).not.toBeInTheDocument();
    // The sheet swaps the `esc` cap for a button, because neither the key nor
    // the legend that names it exists on a phone.
    expect(
      screen.getByRole("button", { name: "Close command palette" }),
    ).toBeInTheDocument();
  });

  it("shows a project's stack as its second line", () => {
    renderPalette();

    const option = screen.getByRole("option", { name: /githmind/i });

    expect(
      within(option).getByText("Next.js · Spring Boot · RAG"),
    ).toBeInTheDocument();
  });
});
