import { describe, expect, it, vi } from "vitest";

import {
  buildCommands,
  commandPages,
  commandProjects,
  findCommand,
  COMMAND_IDS,
  type CommandData,
} from "@/lib/commands/registry";
import { siteConfig } from "@/lib/site-config";

import type { CommandContext } from "@/lib/commands/context";

function makeContext() {
  return {
    navigate: vi.fn(),
    openExternal: vi.fn(),
    setTheme: vi.fn(),
    openChat: vi.fn(),
    openTerminal: vi.fn(),
    closeTerminal: vi.fn(),
    copy: vi.fn(),
    toast: vi.fn(),
  } satisfies CommandContext;
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

describe("commandPages", () => {
  it("starts at home and names each page after its route", () => {
    const pages = commandPages(true);

    expect(pages[0]).toEqual({ id: "home", label: "Home", href: "/" });
    expect(pages.map((page) => page.id)).toEqual([
      "home",
      "about",
      "projects",
      "status",
      "guestbook",
      "contact",
      "tech",
    ]);
  });

  it("leaves out the status page until it is published", () => {
    const ids = commandPages(false).map((page) => page.id);

    expect(ids).not.toContain("status");
    // Everything else keeps its place, so the list is still the site's order.
    expect(ids).toEqual([
      "home",
      "about",
      "projects",
      "guestbook",
      "contact",
      "tech",
    ]);
  });
});

describe("commandProjects", () => {
  it("carries the card's summary and joins the stack the way the export does", () => {
    const [project] = commandProjects([
      {
        slug: "medi-scan",
        title: "Medi-Scan",
        summary: "Upload a lab report and get a plain-language explanation.",
        stack: ["Next.js", "Spring AI", "pgvector"],
        href: "/projects/medi-scan",
      },
    ]);

    expect(project).toEqual({
      id: "medi-scan",
      label: "Medi-Scan",
      summary: "Upload a lab report and get a plain-language explanation.",
      stack: "Next.js · Spring AI · pgvector",
      href: "/projects/medi-scan",
    });
  });
});

describe("buildCommands", () => {
  it("groups Pages, then Projects, then Actions", () => {
    const groups = buildCommands(data, { theme: "dark", touch: false }).map(
      (command) => command.group,
    );

    expect([...new Set(groups)]).toEqual(["Pages", "Projects", "Actions"]);
    // No group is split in two, which is what cmdk's source order relies on.
    expect(groups).toEqual([...groups].sort(
      (a, b) =>
        ["Pages", "Projects", "Actions"].indexOf(a) -
        ["Pages", "Projects", "Actions"].indexOf(b),
    ));
  });

  it("offers the theme the visitor is not using", () => {
    const dark = findCommand(
      buildCommands(data, { theme: "dark", touch: false }),
      COMMAND_IDS.theme,
    );
    const light = findCommand(
      buildCommands(data, { theme: "light", touch: false }),
      COMMAND_IDS.theme,
    );
    const ctx = makeContext();

    expect(dark?.title).toBe("Switch to light theme");
    expect(light?.title).toBe("Switch to dark theme");

    dark?.run(ctx);
    expect(ctx.setTheme).toHaveBeenCalledWith("light");
  });

  it("hides the terminal on a touch-only device", () => {
    const touch = buildCommands(data, { theme: "dark", touch: true });
    const pointer = buildCommands(data, { theme: "dark", touch: false });

    expect(findCommand(touch, COMMAND_IDS.openTerminal)).toBeUndefined();
    expect(findCommand(pointer, COMMAND_IDS.openTerminal)?.kind).toBe(
      "terminal",
    );
  });

  it("navigates for a page and for a project", () => {
    const commands = buildCommands(data, { theme: "dark", touch: false });
    const ctx = makeContext();

    findCommand(commands, COMMAND_IDS.page("guestbook"))?.run(ctx);
    findCommand(commands, COMMAND_IDS.project("githmind"))?.run(ctx);

    expect(ctx.navigate).toHaveBeenNthCalledWith(1, "/guestbook");
    expect(ctx.navigate).toHaveBeenNthCalledWith(2, "/projects/githmind");
  });

  it("copies the configured email and labels the confirmation", () => {
    const ctx = makeContext();

    findCommand(
      buildCommands(data, { theme: "dark", touch: false }),
      COMMAND_IDS.copyEmail,
    )?.run(ctx);

    expect(ctx.copy).toHaveBeenCalledWith(siteConfig.email, "Email copied");
  });

  it("opens the chat with no question of its own", () => {
    const ctx = makeContext();

    findCommand(
      buildCommands(data, { theme: "dark", touch: false }),
      COMMAND_IDS.askAi,
    )?.run(ctx);

    expect(ctx.openChat).toHaveBeenCalledWith();
  });

  it("has one external command per configured social profile", () => {
    const commands = buildCommands(data, { theme: "dark", touch: false });
    const ctx = makeContext();

    for (const link of siteConfig.social) {
      const command = findCommand(commands, COMMAND_IDS.social(link.label));

      expect(command?.title).toBe(`Open ${link.label}`);
      command?.run(ctx);
      expect(ctx.openExternal).toHaveBeenCalledWith(link.href);
    }

    expect(ctx.openExternal).toHaveBeenCalledTimes(siteConfig.social.length);
  });

  it("gives every command a unique id and some search terms", () => {
    const commands = buildCommands(data, { theme: "dark", touch: false });
    const ids = commands.map((command) => command.id);
    const titles = commands.map((command) => command.title);

    expect(new Set(ids).size).toBe(ids.length);
    // Titles have to be unique too, and not only for the reader's sake: the
    // palette hands each row's title to cmdk as its value, and two rows with
    // one value collapse into a single selectable item.
    expect(new Set(titles).size).toBe(titles.length);
    for (const command of commands) {
      expect(command.keywords.length).toBeGreaterThan(0);
      expect(command.title.length).toBeGreaterThan(0);
    }
  });
});
