import { COMMAND_IDS, type CommandData } from "@/lib/commands/registry";
import { siteConfig } from "@/lib/site-config";

import type { ThemeChoice } from "@/lib/commands/context";

/**
 * The terminal's language, as one pure function.
 *
 * `run` takes a line and the same data the palette is built from, and returns
 * what to print, where the prompt now points, and at most one thing for the app
 * to do. It touches no React, no router and no DOM, which is why every command
 * in here is tested by calling it rather than by typing into a rendered dialog.
 *
 * Destinations are never spelled out twice: `cd`, `cat` and `open` all resolve
 * against `CommandData`, and the effect they return names a registry command by
 * id. The terminal cannot navigate somewhere the palette does not offer.
 */

/** Maps onto the export's `.ln`, `.ln dim`, `.ln acc`, `.ln err`, `.ln ok`. */
export type LineTone = "plain" | "dim" | "accent" | "error" | "ok";

export type TerminalLine =
  /** An echoed prompt line: what the visitor typed, and from where. */
  | { kind: "command"; text: string; cwd: string }
  | { kind: "output"; text: string; tone: LineTone };

/** What a command asks the surrounding app to do, if anything. */
export type TerminalEffect =
  /** Run a registry command by id — the shared half of the registry. */
  | { type: "run"; commandId: string }
  /**
   * Set one specific theme. Not a registry command, because the palette's
   * theme row is a toggle and `theme dark` has to mean dark even when dark is
   * already on. Both still go through `CommandContext.setTheme`.
   */
  | { type: "theme"; theme: ThemeChoice }
  | { type: "close" };

export type TerminalResult = {
  /** Output to append below the echoed command. */
  lines: TerminalLine[];
  /** The prompt's path afterwards: "" for home, "/projects" elsewhere. */
  cwd: string;
  /** True when the screen should be wiped rather than appended to. */
  clear: boolean;
  effect: TerminalEffect | null;
};

export type TerminalInput = {
  /** The raw line, as typed. */
  input: string;
  /** Where the prompt points now. */
  cwd: string;
  data: CommandData;
};

/**
 * Every command, its usage and what `help` says about it.
 *
 * One table drives the help listing and Tab completion, so a command cannot be
 * added without becoming discoverable in both.
 */
export const TERMINAL_COMMANDS = [
  { name: "help", usage: "help", summary: "show this list" },
  {
    name: "ls",
    usage: "ls [pages|projects]",
    summary: "list pages or projects",
  },
  { name: "cd", usage: "cd <page>", summary: "go to a page (cd projects)" },
  {
    name: "cat",
    usage: "cat about|<project>",
    summary: "about me, or a project summary",
  },
  {
    name: "open",
    usage: "open <project|github|linkedin>",
    summary: "open a project or a profile",
  },
  {
    name: "theme",
    usage: "theme <dark|light>",
    summary: "switch the theme",
  },
  { name: "whoami", usage: "whoami", summary: "who built this" },
  { name: "contact", usage: "contact", summary: "how to reach me" },
  { name: "clear", usage: "clear", summary: "clear the screen" },
  { name: "exit", usage: "exit", summary: "close the terminal" },
] as const;

/** The widest usage string, so help lines up in one column. */
const USAGE_WIDTH =
  Math.max(...TERMINAL_COMMANDS.map((command) => command.usage.length)) + 2;

/** `ls` and `cat` pad their first column to this. */
const NAME_WIDTH = 12;

const out = (text: string, tone: LineTone = "plain"): TerminalLine => ({
  kind: "output",
  text,
  tone,
});

/** The hostname, which is what the banner and the prompt are named after. */
function host(): string {
  try {
    return new URL(siteConfig.siteUrl).hostname.replace(/^www\./, "");
  } catch {
    return siteConfig.name.toLowerCase();
  }
}

export const TERMINAL_BANNER = host();

/** `visitor@saq` — the left half of the prompt. */
export const TERMINAL_USER = `visitor@${siteConfig.name.toLowerCase()}`;

export function helpLines(): TerminalLine[] {
  return TERMINAL_COMMANDS.map((command) =>
    out(`  ${command.usage.padEnd(USAGE_WIDTH)}${command.summary}`),
  );
}

/** What the screen says before anything has been typed. */
export function welcomeLines(): TerminalLine[] {
  return [
    out("Welcome. This terminal mirrors the real pages of this site.", "dim"),
    out("Nothing here that the site does not already show.", "dim"),
    out(""),
    ...helpLines(),
    out(""),
  ];
}

/** The id a `cd` target resolves to, treating the shell's shorthands as home. */
function resolvePage(data: CommandData, argument: string) {
  if (["", "~", "..", "/"].includes(argument)) return data.pages[0];
  return data.pages.find((page) => page.id === argument);
}

function lsPages(data: CommandData): TerminalLine[] {
  return data.pages.map((page) =>
    out(`  ${page.id.padEnd(NAME_WIDTH)}${page.href}`),
  );
}

function lsProjects(data: CommandData): TerminalLine[] {
  return data.projects.map((project) =>
    out(`  ${project.id.padEnd(NAME_WIDTH)}${project.label}`, "accent"),
  );
}

/** `cat about` — the home page's own introduction, nothing new. */
function aboutLines(): TerminalLine[] {
  return [
    out(`${siteConfig.fullName} — ${siteConfig.role}`, "accent"),
    out(siteConfig.headline),
    out(`based  ${siteConfig.location}`, "dim"),
    out("more   /#about", "dim"),
  ];
}

export function run({ input, cwd, data }: TerminalInput): TerminalResult {
  const parts = input.trim().split(/\s+/).filter(Boolean);
  const name = (parts[0] ?? "").toLowerCase();
  const argument = (parts[1] ?? "").toLowerCase();

  const result = (
    lines: TerminalLine[],
    patch: Partial<Omit<TerminalResult, "lines">> = {},
  ): TerminalResult => ({
    lines,
    cwd,
    clear: false,
    effect: null,
    ...patch,
  });

  // A bare Enter moves the prompt down a line, as every shell does.
  if (!name) return result([]);

  if (name === "help") return result(helpLines());

  if (name === "clear") return result([], { clear: true });

  if (name === "exit") {
    return result([], { effect: { type: "close" } });
  }

  if (name === "ls") {
    if (argument === "pages") return result(lsPages(data));
    if (argument === "projects") return result(lsProjects(data));
    if (argument) {
      return result([out(`ls: ${argument}: no such directory`, "error")]);
    }
    return result([...lsPages(data), out(""), ...lsProjects(data)]);
  }

  if (name === "cd") {
    const page = resolvePage(data, argument);

    if (!page) {
      return result([
        out(`cd: ${argument}: no such page. Try ls pages`, "error"),
      ]);
    }

    return result([out(`→ ${page.href}`, "ok")], {
      // The prompt follows the page, so `~/guestbook` is both where the
      // visitor is and what the rest of the site's chrome is showing.
      cwd: page.id === "home" ? "" : `/${page.id}`,
      effect: { type: "run", commandId: COMMAND_IDS.page(page.id) },
    });
  }

  if (name === "cat") {
    if (argument === "about") return result(aboutLines());

    const project = data.projects.find((entry) => entry.id === argument);

    if (!project) {
      return result([
        out(
          `cat: ${argument || "(missing)"}: no such project. Try ls projects`,
          "error",
        ),
      ]);
    }

    // The same sentence the project card shows, because it is the same field.
    return result([
      out(project.label, "accent"),
      out(project.summary),
      out(`stack  ${project.stack}`, "dim"),
      out(`more   ${project.href}`, "dim"),
    ]);
  }

  if (name === "open") {
    const project = data.projects.find((entry) => entry.id === argument);

    if (project) {
      return result([out(`→ ${project.href}`, "ok")], {
        effect: { type: "run", commandId: COMMAND_IDS.project(project.id) },
      });
    }

    const social = siteConfig.social.find(
      (link) => link.label.toLowerCase() === argument,
    );

    if (social) {
      return result([out(`→ ${social.href}`, "ok")], {
        effect: { type: "run", commandId: COMMAND_IDS.social(social.label) },
      });
    }

    return result([
      out(
        `open: ${argument || "(missing)"}: nothing to open. Try ls projects`,
        "error",
      ),
    ]);
  }

  if (name === "theme") {
    if (argument !== "dark" && argument !== "light") {
      return result([out("usage: theme <dark|light>", "error")]);
    }

    return result([out(`theme set to ${argument}`, "ok")], {
      effect: { type: "theme", theme: argument },
    });
  }

  if (name === "whoami") {
    return result([
      out(`${siteConfig.fullName} — ${siteConfig.role}`),
      out(siteConfig.headline, "dim"),
    ]);
  }

  if (name === "contact") {
    return result([
      out(siteConfig.email, "accent"),
      ...siteConfig.social.map((link) =>
        out(`${link.label.toLowerCase().padEnd(NAME_WIDTH)}${link.href}`),
      ),
      out("more   /#contact", "dim"),
    ]);
  }

  return result([out(`command not found: ${name}. Try help`, "error")]);
}

/**
 * Tab completion, following the export: one unambiguous match completes, and
 * anything else leaves the line alone rather than guessing.
 */
export function complete(input: string, data: CommandData): string {
  const parts = input.split(" ");

  if (parts.length === 1) {
    const matches = TERMINAL_COMMANDS.filter((command) =>
      command.name.startsWith(parts[0]),
    );

    return matches.length === 1 ? `${matches[0].name} ` : input;
  }

  if (parts.length > 2) return input;

  const [name, argument] = parts;
  const pool = argumentPool(name.toLowerCase(), data);
  const matches = pool.filter((candidate) =>
    candidate.startsWith(argument.toLowerCase()),
  );

  return matches.length === 1 ? `${name} ${matches[0]}` : input;
}

/** What the second word of a given command can be. */
function argumentPool(name: string, data: CommandData): string[] {
  if (name === "cd") return data.pages.map((page) => page.id);
  if (name === "cat") {
    return ["about", ...data.projects.map((project) => project.id)];
  }
  if (name === "open") {
    return [
      ...data.projects.map((project) => project.id),
      ...siteConfig.social.map((link) => link.label.toLowerCase()),
    ];
  }
  if (name === "theme") return ["dark", "light"];
  if (name === "ls") return ["pages", "projects"];
  return [];
}
