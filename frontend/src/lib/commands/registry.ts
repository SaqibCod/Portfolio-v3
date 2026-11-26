import { footerSiteNav, primaryNav } from "@/lib/navigation";
import { siteConfig } from "@/lib/site-config";

import type { CommandContext, ThemeChoice } from "@/lib/commands/context";

/**
 * The one list of things this site can be told to do.
 *
 * Both the command palette and the terminal are built from this module and
 * nothing else: the palette renders it as rows, and the terminal's `cd`,
 * `open` and `theme` resolve to the very same `Command` objects by id. That is
 * the point of the phase — two surfaces, one registry — and it is why the
 * errors, the labels and the destinations cannot drift apart.
 *
 * Note what this file does *not* import: `@/lib/projects`. That module pulls
 * in every case study's compiled MDX, so importing it from code that ends up
 * in the browser would ship all three bodies twice. The server passes the
 * handful of fields the overlays need instead — see {@link CommandData}.
 */

export type CommandGroupName = "Pages" | "Projects" | "Actions";

/** Decides which glyph the palette puts in a row's icon tile. */
export type CommandKind = "page" | "project" | "action" | "terminal";

export type CommandPage = {
  /** The name the terminal's `cd` and `ls pages` use, e.g. "guestbook". */
  id: string;
  label: string;
  href: string;
};

export type CommandProject = {
  /** The case study's slug, which is what `cat` and `open` take. */
  id: string;
  label: string;
  /** The same sentence the project card shows. */
  summary: string;
  /** `ProjectMeta.stack`, joined the way the export prints it. */
  stack: string;
  href: string;
};

/** The serialisable half of the registry: what the server has to hand down. */
export type CommandData = {
  pages: CommandPage[];
  projects: CommandProject[];
};

export type Command = {
  id: string;
  title: string;
  /** The second line of a palette row. */
  subtitle: string;
  /** Extra search terms that are not in the title, for cmdk's filter. */
  keywords: string[];
  group: CommandGroupName;
  kind: CommandKind;
  run(ctx: CommandContext): void;
};

export type CommandOptions = {
  /** The theme in force, so the theme action can offer the other one. */
  theme: ThemeChoice;
  /** True on a touch-only device, where there is no terminal to offer. */
  touch: boolean;
};

/**
 * The shape the registry needs out of a `Project`, named structurally so this
 * module stays free of the MDX import. `@/lib/projects`' `Project` satisfies it.
 */
type ProjectSource = {
  slug: string;
  title: string;
  summary: string;
  stack: readonly string[];
  href: string;
};

/**
 * "/projects" -> "projects", "/#contact" -> "contact", "/" -> "home".
 *
 * The id is what a visitor types after `cd`, so it is derived from the href
 * rather than stored next to it: a nav entry that moves takes its command name
 * with it, and there is no second place to forget to update.
 */
function pageId(href: string): string {
  const [path, hash] = href.split("#");
  return path.replaceAll("/", "") || hash || "home";
}

/**
 * Every page the palette may offer, in the order the site presents them.
 *
 * Built from `primaryNav`, so `/status` is absent exactly when the status page
 * is unpublished — a palette that offered it would be sending people to a 404.
 * The footer's extra entry (Tech behind this site) is appended because it is a
 * real page that simply is not in the header.
 */
export function commandPages(statusPublished: boolean): CommandPage[] {
  const nav = primaryNav(statusPublished);
  const footerOnly = footerSiteNav(statusPublished).filter(
    (item) => !nav.some((navItem) => navItem.href === item.href),
  );

  return [{ id: "home", label: "Home", href: "/" }].concat(
    [...nav, ...footerOnly].map((item) => ({
      id: pageId(item.href),
      label: item.label,
      href: item.href,
    })),
  );
}

/** Narrows case studies to the fields the overlays show. */
export function commandProjects(
  projects: readonly ProjectSource[],
): CommandProject[] {
  return projects.map((project) => ({
    id: project.slug,
    label: project.title,
    summary: project.summary,
    stack: project.stack.join(" · "),
    href: project.href,
  }));
}

/** Ids of the commands the terminal reaches for by name. */
export const COMMAND_IDS = {
  theme: "action:theme",
  copyEmail: "action:copy-email",
  askAi: "action:ask-ai",
  openTerminal: "action:open-terminal",
  page: (id: string) => `page:${id}`,
  project: (id: string) => `project:${id}`,
  social: (label: string) => `action:open-${label.toLowerCase()}`,
} as const;

/**
 * The full list, grouped the way the export groups it: Pages, Projects, then
 * Actions. Order matters — cmdk keeps groups in source order, and the export
 * puts the destinations above the verbs.
 */
export function buildCommands(
  data: CommandData,
  { theme, touch }: CommandOptions,
): Command[] {
  const other: ThemeChoice = theme === "dark" ? "light" : "dark";

  const pages: Command[] = data.pages.map((page) => ({
    id: COMMAND_IDS.page(page.id),
    title: page.label,
    subtitle: page.href,
    keywords: [page.id, "page", "go to", "jump to"],
    group: "Pages",
    kind: "page",
    run: (ctx) => ctx.navigate(page.href),
  }));

  const projects: Command[] = data.projects.map((project) => ({
    id: COMMAND_IDS.project(project.id),
    title: project.label,
    subtitle: project.stack,
    keywords: [project.id, "project", "case study", "work"],
    group: "Projects",
    kind: "project",
    run: (ctx) => ctx.navigate(project.href),
  }));

  const actions: Command[] = [
    {
      id: COMMAND_IDS.theme,
      title: `Switch to ${other} theme`,
      subtitle: "Appearance",
      keywords: ["theme", "dark", "light", "appearance", "colour", "color"],
      group: "Actions",
      kind: "action",
      run: (ctx) => ctx.setTheme(other),
    },
    {
      id: COMMAND_IDS.copyEmail,
      title: "Copy email address",
      subtitle: siteConfig.email,
      keywords: ["email", "mail", "copy", "contact", "hire", "reach"],
      group: "Actions",
      kind: "action",
      run: (ctx) => ctx.copy(siteConfig.email, "Email copied"),
    },
    {
      id: COMMAND_IDS.askAi,
      title: "Ask the AI about me",
      subtitle: "Opens the chat",
      keywords: ["ai", "chat", "ask", "question", "bot"],
      group: "Actions",
      kind: "action",
      run: (ctx) => ctx.openChat(),
    },
    ...siteConfig.social.map((link) => ({
      id: COMMAND_IDS.social(link.label),
      title: `Open ${link.label}`,
      subtitle: link.href.replace(/^https?:\/\//, ""),
      keywords: [link.label.toLowerCase(), "profile", "link", "social"],
      group: "Actions" as const,
      kind: "action" as const,
      run: (ctx: CommandContext) => ctx.openExternal(link.href),
    })),
  ];

  // A terminal needs a keyboard and a prompt. Offering it on a phone would
  // hand someone an on-screen keyboard and no Tab key, so the row is simply
  // not in the list there — the same condition the export's `touch` prop sets.
  if (!touch) {
    actions.push({
      id: COMMAND_IDS.openTerminal,
      title: "Open terminal",
      subtitle: "Explore the site with commands",
      keywords: ["terminal", "cli", "shell", "console", "command line"],
      group: "Actions",
      kind: "terminal",
      run: (ctx) => ctx.openTerminal(),
    });
  }

  return [...pages, ...projects, ...actions];
}

/** The lookup the terminal uses so it never re-implements an action. */
export function findCommand(
  commands: readonly Command[],
  id: string,
): Command | undefined {
  return commands.find((command) => command.id === id);
}
