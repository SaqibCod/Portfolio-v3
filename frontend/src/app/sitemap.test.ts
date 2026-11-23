import { beforeEach, describe, expect, it, vi } from "vitest";

import robots from "@/app/robots";
import sitemap from "@/app/sitemap";
import { getAllProjects } from "@/lib/projects";
import { siteConfig } from "@/lib/site-config";

const statusPageIsPublished = vi.hoisted(() => vi.fn());

vi.mock("@/lib/status", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/status")>()),
  statusPageIsPublished,
}));

describe("sitemap", () => {
  beforeEach(() => {
    statusPageIsPublished.mockResolvedValue(true);
  });

  it("lists every static route", async () => {
    const urls = (await sitemap()).map((entry) => entry.url);

    expect(urls).toContain(siteConfig.siteUrl);
    expect(urls).toContain(`${siteConfig.siteUrl}/projects`);
    expect(urls).toContain(`${siteConfig.siteUrl}/contact`);
    expect(urls).toContain(`${siteConfig.siteUrl}/tech`);
    expect(urls).toContain(`${siteConfig.siteUrl}/guestbook`);
  });

  it("lists every project slug", async () => {
    const urls = (await sitemap()).map((entry) => entry.url);

    for (const project of getAllProjects()) {
      expect(urls).toContain(`${siteConfig.siteUrl}${project.href}`);
    }
  });

  it("takes each case study's lastModified from its front matter", async () => {
    const entries = await sitemap();

    for (const project of getAllProjects()) {
      const entry = entries.find(
        (candidate) =>
          candidate.url === `${siteConfig.siteUrl}${project.href}`,
      );

      expect(entry?.lastModified).toBe(project.updated);
    }
  });

  it("uses absolute URLs and never repeats one", async () => {
    const urls = (await sitemap()).map((entry) => entry.url);

    for (const url of urls) {
      expect(url.startsWith("https://")).toBe(true);
    }
    expect(new Set(urls).size).toBe(urls.length);
  });

  it("lists /status while a project is monitored", async () => {
    const urls = (await sitemap()).map((entry) => entry.url);

    expect(urls).toContain(`${siteConfig.siteUrl}/status`);
  });

  it("omits /status when nothing is monitored", async () => {
    // The route 404s in that configuration, and advertising a URL that 404s is
    // worse than leaving it out.
    statusPageIsPublished.mockResolvedValue(false);

    const urls = (await sitemap()).map((entry) => entry.url);

    expect(urls).not.toContain(`${siteConfig.siteUrl}/status`);
  });

  it("never lists the owner's moderation page", async () => {
    // /admin exists but is noindex and disallowed in robots.txt. Listing it
    // here would advertise the URL it is the whole point of not advertising.
    const urls = (await sitemap()).map((entry) => entry.url);

    expect(urls).not.toContain(`${siteConfig.siteUrl}/admin`);
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
