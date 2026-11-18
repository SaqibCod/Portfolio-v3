import { describe, expect, it } from "vitest";

import { breadcrumbJsonLd, personJsonLd, projectJsonLd } from "@/lib/jsonld";
import { getAllProjects, getProject } from "@/lib/projects";
import { siteConfig } from "@/lib/site-config";

describe("personJsonLd", () => {
  const person = personJsonLd();

  it("is a Person node with a stable @id", () => {
    expect(person["@context"]).toBe("https://schema.org");
    expect(person["@type"]).toBe("Person");
    expect(person["@id"]).toBe(`${siteConfig.siteUrl}/#person`);
  });

  it("carries the fields a rich result needs", () => {
    expect(person.name).toBe(siteConfig.name);
    expect(person.jobTitle).toBe(siteConfig.role);
    expect(person.url).toBe(siteConfig.siteUrl);
    expect(person.image).toMatch(/^https:\/\//);
    expect(person.email).toBe(`mailto:${siteConfig.email}`);
  });

  it("lists every social profile in sameAs", () => {
    expect(person.sameAs).toEqual(siteConfig.social.map((link) => link.href));
  });
});

describe("projectJsonLd", () => {
  it("describes the page as a CreativeWork about a SoftwareApplication", () => {
    const project = getProject("githmind");
    expect(project).toBeDefined();

    const data = projectJsonLd(project!);

    expect(data["@type"]).toBe("CreativeWork");
    expect(data.url).toBe(`${siteConfig.siteUrl}/projects/githmind`);
    expect(data.datePublished).toBe(project!.date);
    expect(data.dateModified).toBe(project!.updated);
    expect(data.author).toEqual({ "@id": `${siteConfig.siteUrl}/#person` });
    expect(data.about).toMatchObject({
      "@type": "SoftwareApplication",
      name: "githMind",
    });
  });

  it("always resolves an absolute image, falling back to the OG route", () => {
    for (const project of getAllProjects()) {
      const data = projectJsonLd(project);
      expect(data.image).toMatch(/^https:\/\//);
      if (!project.cover) {
        expect(data.image).toBe(
          `${siteConfig.siteUrl}${project.href}/opengraph-image`,
        );
      }
    }
  });
});

describe("breadcrumbJsonLd", () => {
  it("numbers items from 1 and makes every item absolute", () => {
    const data = breadcrumbJsonLd([
      { name: "Projects", path: "/projects" },
      { name: "githMind", path: "/projects/githmind" },
    ]);

    expect(data["@type"]).toBe("BreadcrumbList");
    expect(data.itemListElement).toEqual([
      {
        "@type": "ListItem",
        position: 1,
        name: "Projects",
        item: `${siteConfig.siteUrl}/projects`,
      },
      {
        "@type": "ListItem",
        position: 2,
        name: "githMind",
        item: `${siteConfig.siteUrl}/projects/githmind`,
      },
    ]);
  });
});
