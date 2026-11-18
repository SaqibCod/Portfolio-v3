import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { caseStudySections, getProject, getProjectSlugs } from "@/lib/projects";

import CaseStudyPage, { generateMetadata, generateStaticParams } from "./page";

/** Renders the server component the way the App Router calls it. */
async function renderCaseStudy(slug: string) {
  const ui = await CaseStudyPage({
    params: Promise.resolve({ slug }),
  } as never);

  return render(ui);
}

describe("generateStaticParams", () => {
  it("covers every case study on disk", () => {
    expect(generateStaticParams()).toEqual(
      getProjectSlugs().map((slug) => ({ slug })),
    );
  });
});

describe("generateMetadata", () => {
  it("builds a unique title, description and canonical per project", async () => {
    const project = getProject("medi-scan")!;
    const metadata = await generateMetadata({
      params: Promise.resolve({ slug: "medi-scan" }),
    } as never);

    expect(metadata.title).toBe(project.title);
    expect(metadata.description).toBe(project.summary);
    expect(metadata.alternates?.canonical).toBe("/projects/medi-scan");
  });
});

describe("a case study page", () => {
  it("renders the project title as the only level-one heading", async () => {
    const project = getProject("githmind")!;
    await renderCaseStudy("githmind");

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent(project.title);
  });

  it("renders the MDX body, not just the header", async () => {
    await renderCaseStudy("githmind");

    for (const section of caseStudySections) {
      expect(
        screen.getByRole("heading", { level: 2, name: section.label }),
      ).toBeInTheDocument();
    }
  });

  it("gives the on-this-page nav a live anchor for every section", async () => {
    const { container } = await renderCaseStudy("githmind");
    const nav = screen.getByRole("navigation", { name: "On this page" });

    for (const section of caseStudySections) {
      const link = within(nav).getByRole("link", { name: section.label });
      expect(link).toHaveAttribute(
        "href",
        `/projects/githmind#${section.id}`,
      );
      expect(container.querySelector(`#${section.id}`)).not.toBeNull();
    }
  });

  it("emits CreativeWork and BreadcrumbList structured data", async () => {
    const { container } = await renderCaseStudy("githmind");

    const blocks = Array.from(
      container.querySelectorAll('script[type="application/ld+json"]'),
    ).map((script) => JSON.parse(script.textContent ?? "{}"));

    expect(blocks.map((block) => block["@type"])).toEqual([
      "CreativeWork",
      "BreadcrumbList",
    ]);
  });

  it("pages to the next case study", async () => {
    await renderCaseStudy("githmind");
    const nav = screen.getByRole("navigation", { name: "More projects" });

    expect(within(nav).getAllByRole("link").length).toBeGreaterThan(0);
  });
});
