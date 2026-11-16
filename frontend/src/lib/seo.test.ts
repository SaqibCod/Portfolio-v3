import { describe, expect, it } from "vitest";

import { absoluteUrl, buildMetadata, normalizePath } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

describe("normalizePath", () => {
  it("keeps the root path as a single slash", () => {
    expect(normalizePath("/")).toBe("/");
    expect(normalizePath("")).toBe("/");
  });

  it("adds a leading slash and strips trailing slashes", () => {
    expect(normalizePath("projects")).toBe("/projects");
    expect(normalizePath("/projects/")).toBe("/projects");
    expect(normalizePath("/projects///")).toBe("/projects");
  });
});

describe("absoluteUrl", () => {
  it("resolves a path against the site origin", () => {
    expect(absoluteUrl("/projects")).toBe(`${siteConfig.siteUrl}/projects`);
    expect(absoluteUrl("/")).toBe(siteConfig.siteUrl);
  });

  it("passes absolute URLs through untouched", () => {
    expect(absoluteUrl("https://cdn.example.com/og.png")).toBe(
      "https://cdn.example.com/og.png",
    );
  });
});

describe("buildMetadata", () => {
  const options = {
    title: "Projects",
    description: "Everything I've built.",
    path: "/projects",
  };

  it("sets a site-relative canonical", () => {
    expect(buildMetadata(options).alternates?.canonical).toBe("/projects");
  });

  it("builds an absolute Open Graph URL from the path", () => {
    expect(buildMetadata(options).openGraph?.url).toBe(
      `${siteConfig.siteUrl}/projects`,
    );
  });

  it("carries the title and description into both card formats", () => {
    const metadata = buildMetadata(options);

    expect(metadata.title).toBe("Projects");
    expect(metadata.openGraph?.title).toBe("Projects");
    expect(metadata.twitter?.title).toBe("Projects");
    expect(metadata.openGraph?.description).toBe(options.description);
    expect(metadata.twitter?.description).toBe(options.description);
  });

  it("requests a large summary card with an absolute image", () => {
    const metadata = buildMetadata(options);

    expect(metadata.twitter).toMatchObject({
      card: "summary_large_image",
      images: [`${siteConfig.siteUrl}/opengraph-image`],
    });
  });

  it("accepts an explicit image override", () => {
    const metadata = buildMetadata({ ...options, image: "/og/projects.png" });

    expect(metadata.twitter?.images).toEqual([
      `${siteConfig.siteUrl}/og/projects.png`,
    ]);
  });

  it("indexes by default and opts out only when asked", () => {
    expect(buildMetadata(options).robots).toBeUndefined();
    expect(buildMetadata({ ...options, noIndex: true }).robots).toEqual({
      index: false,
      follow: false,
    });
  });
});
