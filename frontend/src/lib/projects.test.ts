import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";

import { describe, expect, it } from "vitest";

import {
  caseStudySections,
  getAllProjects,
  getFeaturedProjects,
  getProject,
  getProjectNeighbours,
  getProjectSlugs,
} from "@/lib/projects";

const contentDir = path.join(process.cwd(), "src/content/projects");

function mdxFiles(): string[] {
  return readdirSync(contentDir).filter((file) => file.endsWith(".mdx"));
}

describe("the project registry", () => {
  it("loads every .mdx file in src/content/projects", () => {
    // The registry imports each file by name, so a new case study that nobody
    // registered would otherwise be silently invisible.
    const slugsOnDisk = mdxFiles()
      .map((file) => file.replace(/\.mdx$/, ""))
      .sort();

    expect(getProjectSlugs().slice().sort()).toEqual(slugsOnDisk);
  });

  it("validates meta at import time, so a bad file fails the build", () => {
    // Reaching this test at all means every meta object parsed: an invalid one
    // throws while `@/lib/projects` is being imported.
    for (const project of getAllProjects()) {
      expect(project.title.length).toBeGreaterThan(0);
      expect(project.summary.length).toBeLessThanOrEqual(240);
      expect(project.stack.length).toBeGreaterThan(0);
      expect(project.tags.length).toBeGreaterThan(0);
      expect(project.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(project.updated).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    }
  });

  it("sorts newest first and numbers the list in that order", () => {
    const projects = getAllProjects();
    const dates = projects.map((project) => project.date);

    expect(dates.slice().sort().reverse()).toEqual(dates);
    expect(projects.map((project) => project.number)).toEqual(
      projects.map((_, index) => String(index + 1).padStart(2, "0")),
    );
  });

  it("gives every project a unique slug and a case-study route", () => {
    const slugs = getProjectSlugs();

    expect(new Set(slugs).size).toBe(slugs.length);
    for (const project of getAllProjects()) {
      expect(project.href).toBe(`/projects/${project.slug}`);
    }
  });

  it("resolves a known slug and rejects an unknown one", () => {
    expect(getProject("githmind")?.title).toBe("githMind");
    expect(getProject("not-a-project")).toBeUndefined();
  });

  it("features at least one project on the home page", () => {
    expect(getFeaturedProjects().length).toBeGreaterThan(0);
  });

  it("pages between neighbours without wrapping around", () => {
    const projects = getAllProjects();
    const first = getProjectNeighbours(projects[0].slug);
    const last = getProjectNeighbours(projects[projects.length - 1].slug);

    expect(first.previous).toBeUndefined();
    expect(first.next?.slug).toBe(projects[1].slug);
    expect(last.next).toBeUndefined();
    expect(last.previous?.slug).toBe(projects[projects.length - 2].slug);
    expect(getProjectNeighbours("not-a-project")).toEqual({});
  });
});

describe("the case-study template", () => {
  it.each(mdxFiles())(
    "%s carries every heading the on-this-page nav links to",
    (file) => {
      const source = readFileSync(path.join(contentDir, file), "utf8");

      // The nav is a constant rather than something parsed out of the MDX, so
      // a renamed heading has to fail here instead of becoming a dead anchor.
      for (const section of caseStudySections) {
        expect(source).toContain(`## ${section.label}`);
      }
    },
  );
});
