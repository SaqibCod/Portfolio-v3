import { describe, expect, it } from "vitest";

import { footerSiteNav, primaryNav } from "@/lib/navigation";
import { siteConfig } from "@/lib/site-config";

describe("primaryNav", () => {
  it("includes Status while a project is monitored", () => {
    const hrefs = primaryNav(true).map((item) => item.href);

    expect(hrefs).toEqual(siteConfig.nav.map((item) => item.href));
  });

  it("drops Status when nothing is monitored", () => {
    const hrefs = primaryNav(false).map((item) => item.href);

    expect(hrefs).not.toContain("/status");
  });

  it("leaves every other link alone, in order", () => {
    // Adding the first monitored project has to put Status back exactly where
    // the design has it, so the rest of the row must be untouched.
    expect(primaryNav(false).map((item) => item.href)).toEqual(
      siteConfig.nav
        .map((item) => item.href)
        .filter((href) => href !== "/status"),
    );
  });
});

describe("footerSiteNav", () => {
  it("includes Status while a project is monitored", () => {
    expect(footerSiteNav(true).map((item) => item.href)).toContain("/status");
  });

  it("drops Status when nothing is monitored", () => {
    expect(footerSiteNav(false).map((item) => item.href)).not.toContain(
      "/status",
    );
  });

  it("keeps the rest of the column", () => {
    expect(footerSiteNav(false).map((item) => item.href)).toEqual([
      "/projects",
      "/guestbook",
      "/tech",
    ]);
  });
});
