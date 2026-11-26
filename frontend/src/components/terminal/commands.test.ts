import { describe, expect, it } from "vitest";

import {
  complete,
  run,
  TERMINAL_COMMANDS,
  welcomeLines,
  type TerminalLine,
} from "@/components/terminal/commands";
import {
  COMMAND_IDS,
  commandPages,
  commandProjects,
  type CommandData,
} from "@/lib/commands/registry";
import { siteConfig } from "@/lib/site-config";

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
    {
      slug: "medi-scan",
      title: "Medi-Scan",
      summary: "Upload a lab report and get a plain-language explanation.",
      stack: ["Spring AI", "pgvector"],
      href: "/projects/medi-scan",
    },
  ]),
};

/** The printed text, which is what a visitor actually sees. */
function text(lines: TerminalLine[]): string {
  return lines.map((line) => line.text).join("\n");
}

function exec(input: string, cwd = "") {
  return run({ input, cwd, data });
}

describe("help", () => {
  it("lists every command the terminal has", () => {
    const listed = text(exec("help").lines);

    for (const command of TERMINAL_COMMANDS) {
      expect(listed).toContain(command.usage);
    }
  });

  it("is already on screen before anything is typed", () => {
    // A prompt with no visible way in is a dead end for most visitors.
    expect(text(welcomeLines())).toContain("ls [pages|projects]");
  });
});

describe("ls", () => {
  it("lists pages with the routes they go to", () => {
    const listed = text(exec("ls pages").lines);

    expect(listed).toContain("guestbook");
    expect(listed).toContain("/guestbook");
    expect(listed).not.toContain("githmind");
  });

  it("lists projects by slug and name", () => {
    const listed = text(exec("ls projects").lines);

    expect(listed).toContain("medi-scan");
    expect(listed).toContain("Medi-Scan");
    expect(listed).not.toContain("/guestbook");
  });

  it("lists both, separated, with no argument", () => {
    const listed = text(exec("ls").lines);

    expect(listed).toContain("/guestbook");
    expect(listed).toContain("githMind");
  });

  it("reports a directory it does not have", () => {
    const [line] = exec("ls nonsense").lines;

    expect(line).toMatchObject({
      text: "ls: nonsense: no such directory",
      tone: "error",
    });
  });
});

describe("cd", () => {
  it("navigates through the registry and moves the prompt", () => {
    const result = exec("cd guestbook");

    expect(text(result.lines)).toBe("→ /guestbook");
    expect(result.cwd).toBe("/guestbook");
    // Not its own router call: the same command the palette's row runs.
    expect(result.effect).toEqual({
      type: "run",
      commandId: COMMAND_IDS.page("guestbook"),
    });
  });

  it("treats the shell's shorthands as home", () => {
    for (const argument of ["", "~", "..", "/"]) {
      const result = exec(`cd ${argument}`);

      expect(result.cwd).toBe("");
      expect(result.effect).toEqual({
        type: "run",
        commandId: COMMAND_IDS.page("home"),
      });
    }
  });

  it("refuses a page that does not exist and says how to look", () => {
    const result = exec("cd nowhere");

    expect(result.lines[0]).toMatchObject({
      text: "cd: nowhere: no such page. Try ls pages",
      tone: "error",
    });
    expect(result.effect).toBeNull();
    expect(result.cwd).toBe("");
  });

  it("cannot reach a page the palette does not offer", () => {
    // `/status` is a route only while something is monitored, and the data is
    // built from the same `commandPages` the palette uses.
    const unpublished: CommandData = {
      pages: commandPages(false),
      projects: [],
    };

    const result = run({ input: "cd status", cwd: "", data: unpublished });

    expect(result.effect).toBeNull();
    expect(result.lines[0]?.text).toContain("no such page");
  });
});

describe("cat", () => {
  it("prints the same summary as the project card", () => {
    const result = exec("cat githmind");

    expect(text(result.lines)).toBe(
      [
        "githMind",
        "Chat with any GitHub repository.",
        "stack  Next.js · Spring Boot · RAG",
        "more   /projects/githmind",
      ].join("\n"),
    );
  });

  it("prints the site's own introduction for `about`", () => {
    const printed = text(exec("cat about").lines);

    expect(printed).toContain(siteConfig.fullName);
    expect(printed).toContain(siteConfig.headline);
  });

  it("names the missing argument", () => {
    expect(exec("cat").lines[0]).toMatchObject({
      text: "cat: (missing): no such project. Try ls projects",
      tone: "error",
    });
  });
});

describe("open", () => {
  it("opens a project through its registry command", () => {
    expect(exec("open medi-scan").effect).toEqual({
      type: "run",
      commandId: COMMAND_IDS.project("medi-scan"),
    });
  });

  it("opens a configured profile", () => {
    const [social] = siteConfig.social;

    expect(exec(`open ${social.label.toLowerCase()}`).effect).toEqual({
      type: "run",
      commandId: COMMAND_IDS.social(social.label),
    });
  });

  it("refuses anything else", () => {
    expect(exec("open sesame").lines[0]).toMatchObject({ tone: "error" });
  });
});

describe("theme", () => {
  it("sets the theme it is given", () => {
    const result = exec("theme light");

    expect(text(result.lines)).toBe("theme set to light");
    expect(result.effect).toEqual({ type: "theme", theme: "light" });
  });

  it("sets dark even when dark is already on", () => {
    // Why this is not the palette's theme row: that one toggles, and
    // `theme dark` has to mean dark.
    expect(exec("theme dark").effect).toEqual({
      type: "theme",
      theme: "dark",
    });
  });

  it("asks for an argument it understands", () => {
    for (const input of ["theme", "theme blue"]) {
      expect(exec(input).lines[0]).toMatchObject({
        text: "usage: theme <dark|light>",
        tone: "error",
      });
    }
  });
});

describe("the rest", () => {
  it("says who built the site, in the site's own words", () => {
    const printed = text(exec("whoami").lines);

    expect(printed).toContain(siteConfig.fullName);
    expect(printed).toContain(siteConfig.role);
  });

  it("gives the real address and profiles for contact", () => {
    const printed = text(exec("contact").lines);

    expect(printed).toContain(siteConfig.email);
    expect(printed).toContain(siteConfig.social[0].href);
  });

  it("clears the screen without printing anything", () => {
    const result = exec("clear");

    expect(result.clear).toBe(true);
    expect(result.lines).toEqual([]);
  });

  it("closes on exit", () => {
    expect(exec("exit").effect).toEqual({ type: "close" });
  });

  it("does nothing at all on a bare Enter", () => {
    const result = exec("   ");

    expect(result.lines).toEqual([]);
    expect(result.effect).toBeNull();
    expect(result.clear).toBe(false);
  });

  it("reports an unknown command and points at help", () => {
    expect(exec("sudo hire-me").lines[0]).toMatchObject({
      text: "command not found: sudo. Try help",
      tone: "error",
    });
  });

  it("is case-insensitive about command names", () => {
    expect(exec("HELP").lines.length).toBe(TERMINAL_COMMANDS.length);
  });
});

describe("complete", () => {
  it("completes an unambiguous command and adds the space", () => {
    expect(complete("wh", data)).toBe("whoami ");
  });

  it("leaves an ambiguous prefix alone", () => {
    // `c` is cd, cat, contact and clear — guessing would be worse than
    // waiting for another keystroke.
    expect(complete("c", data)).toBe("c");
  });

  it("completes a page for cd", () => {
    expect(complete("cd guest", data)).toBe("cd guestbook");
  });

  it("completes a project for cat, and `about` too", () => {
    expect(complete("cat medi", data)).toBe("cat medi-scan");
    expect(complete("cat ab", data)).toBe("cat about");
  });

  it("completes a profile name for open", () => {
    expect(complete("open githu", data)).toBe("open github");
  });

  it("will not pick between a project and a profile", () => {
    // `open` draws from both pools, and "git" is the start of githmind and
    // github alike.
    expect(complete("open git", data)).toBe("open git");
  });

  it("completes a theme", () => {
    expect(complete("theme l", data)).toBe("theme light");
  });

  it("has nothing to offer for a command that takes no argument", () => {
    expect(complete("whoami ", data)).toBe("whoami ");
  });
});
