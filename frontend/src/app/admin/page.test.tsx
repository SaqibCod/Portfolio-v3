import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import robots from "@/app/robots";
import sitemap from "@/app/sitemap";
import { siteConfig } from "@/lib/site-config";

import AdminPage, { metadata } from "./page";

const openModerationQueue = vi.hoisted(() => vi.fn());

vi.mock("@/lib/admin-guestbook", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/admin-guestbook")>()),
  openModerationQueue,
}));

describe("Admin page", () => {
  it("is kept out of search results", () => {
    // The acceptance criterion, and the reason this page has a test at all.
    expect(metadata.robots).toEqual({ index: false, follow: false });
  });

  it("is absent from the sitemap", async () => {
    const urls = (await sitemap()).map((entry) => entry.url);

    expect(urls).not.toContain(`${siteConfig.siteUrl}/admin`);
  });

  it("is disallowed in robots.txt", () => {
    const rules = robots().rules;

    expect(Array.isArray(rules) ? rules[0] : rules).toMatchObject({
      disallow: "/admin",
    });
  });

  it("exports a canonical URL, like every other route", () => {
    expect(metadata.title).toBe("Admin");
    expect(metadata.alternates?.canonical).toBe("/admin");
  });

  it("shows the token prompt and nothing else before it is unlocked", () => {
    render(<AdminPage />);

    expect(screen.getByLabelText("Admin token")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: "Admin" }),
    ).toBeInTheDocument();
    // No entry is rendered, and no request is made, until a token is given.
    expect(openModerationQueue).not.toHaveBeenCalled();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
  });

  it("renders a single level-one heading", () => {
    render(<AdminPage />);

    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
  });

  it("says on the page itself that it is not indexed", () => {
    render(<AdminPage />);

    expect(screen.getByText("noindex")).toBeInTheDocument();
  });

  it("emits no structured data", () => {
    const { container } = render(<AdminPage />);

    expect(
      container.querySelector('script[type="application/ld+json"]'),
    ).toBeNull();
  });
});
