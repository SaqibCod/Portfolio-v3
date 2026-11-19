import { describe, expect, it } from "vitest";

import robots from "@/app/robots";
import sitemap from "@/app/sitemap";
import { getAllProjects } from "@/lib/projects";
import { siteConfig } from "@/lib/site-config";

describe("sitemap", () => {
  const entries = sitemap();
  const urls = entries.map((entry) => entry.url);

  it("lists every static route", () => {
    expect(urls).toContain(siteConfig.siteUrl);
    expect(urls).toContain(`${siteConfig.siteUrl}/projects`);
    expect(urls).toContain(`${siteConfig.siteUrl}/tech`);
  });

  it("lists every project slug", () => {
    for (const project of getAllProjects()) {
      expect(urls).toContain(`${siteConfig.siteUrl}${project.href}`);
    }
  });

  it("takes each case study's lastModified from its front matter", () => {
    for (const project of getAllProjects()) {
      const entry = entries.find(
        (candidate) =>
          candidate.url === `${siteConfig.siteUrl}${project.href}`,
      );

      expect(entry?.lastModified).toBe(project.updated);
    }
  });

  it("uses absolute URLs and never repeats one", () => {
    for (const url of urls) {
      expect(url.startsWith("https://")).toBe(true);
    }
    expect(new Set(urls).size).toBe(urls.length);
  });

  it("omits routes that do not exist yet", () => {
    expect(urls).not.toContain(`${siteConfig.siteUrl}/status`);
    expect(urls).not.toContain(`${siteConfig.siteUrl}/guestbook`);
  });
});

describe("robots", () => {
  const result = robots();

  it("allows everything except the moderation area", () => {
    expect(result.rules).toEqual({
      userAgent: "*",
      allow: "/",
      disallow: "/admin",
    });
  });

  it("points crawlers at the sitemap", () => {
    expect(result.sitemap).toBe(`${siteConfig.siteUrl}/sitemap.xml`);
  });
});
