import { render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import NotFound from "./not-found";

const statusPageIsPublished = vi.hoisted(() => vi.fn());

vi.mock("@/lib/status", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/status")>()),
  statusPageIsPublished,
}));

beforeEach(() => {
  statusPageIsPublished.mockResolvedValue(true);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("Not found page", () => {
  it("offers Status among the popular pages while it exists", async () => {
    render(await NotFound());
    const nav = screen.getByRole("navigation", { name: "Popular pages" });

    expect(within(nav).getByRole("link", { name: /Status/ })).toHaveAttribute(
      "href",
      "/status",
    );
  });

  it("does not send someone from one 404 to another", async () => {
    // The whole point of the page is to help; a link to a page that also 404s
    // does the opposite.
    statusPageIsPublished.mockResolvedValue(false);

    render(await NotFound());
    const nav = screen.getByRole("navigation", { name: "Popular pages" });

    expect(
      within(nav).queryByRole("link", { name: /Status/ }),
    ).not.toBeInTheDocument();
  });

  it("keeps the other three suggestions either way", async () => {
    statusPageIsPublished.mockResolvedValue(false);

    render(await NotFound());
    const nav = screen.getByRole("navigation", { name: "Popular pages" });

    expect(within(nav).getAllByRole("link")).toHaveLength(3);
    expect(within(nav).getByRole("link", { name: /Home/ })).toBeInTheDocument();
    expect(
      within(nav).getByRole("link", { name: /Projects/ }),
    ).toBeInTheDocument();
    expect(
      within(nav).getByRole("link", { name: /Guestbook/ }),
    ).toBeInTheDocument();
  });

  it("lays three cards out in three columns rather than leaving a hole", async () => {
    statusPageIsPublished.mockResolvedValue(false);

    render(await NotFound());

    expect(
      screen.getByRole("navigation", { name: "Popular pages" }).className,
    ).toContain("md:grid-cols-3");
  });

  it("still names the error and offers a way home", async () => {
    render(await NotFound());

    expect(screen.getByRole("heading", { level: 1, name: "404" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Back to Home" }),
    ).toHaveAttribute("href", "/");
  });
});
