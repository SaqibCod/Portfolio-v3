import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { siteConfig } from "@/lib/site-config";

import Home from "./page";

const fetchGithubSummary = vi.hoisted(() => vi.fn());
const fetchGithubActivity = vi.hoisted(() => vi.fn());

vi.mock("@/lib/github", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/github")>()),
  fetchGithubSummary,
  fetchGithubActivity,
}));

/** Enough of a summary to render the stats card. */
function summary() {
  return {
    username: "SaqibCod",
    totalContributions: 1284,
    contributions: [{ date: "2026-11-20", count: 4 }],
    topLanguages: [{ name: "Java", percentage: 48, color: "#b07219" }],
    updatedAt: "2026-11-21T08:00:00Z",
  };
}

function activity() {
  return {
    items: [
      {
        type: "commit" as const,
        repo: "githmind",
        title: "Add streaming responses to chat endpoint",
        url: "https://github.com/SaqibCod/githmind/commit/abc",
        occurredAt: "2026-11-21T06:00:00Z",
      },
    ],
    updatedAt: "2026-11-21T08:00:00Z",
  };
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("Home", () => {
  it("renders a single level-one heading", async () => {
    fetchGithubSummary.mockResolvedValue(null);
    fetchGithubActivity.mockResolvedValue(null);

    render(await Home());

    expect(
      screen.getByRole("heading", {
        level: 1,
        name: `Hi, I’m ${siteConfig.name}`,
      }),
    ).toBeInTheDocument();
  });

  it("server-renders the GitHub stats into the page itself", async () => {
    fetchGithubSummary.mockResolvedValue(summary());
    fetchGithubActivity.mockResolvedValue(activity());

    render(await Home());

    // The acceptance criterion: the figures are in the HTML, with no
    // client-side fetch to wait for.
    expect(screen.getByText("1,284")).toBeInTheDocument();
    expect(screen.getByText("Java 48%")).toBeInTheDocument();
    expect(
      screen.getByText("Add streaming responses to chat endpoint"),
    ).toBeInTheDocument();
  });

  it("falls back quietly when the activity endpoint is unavailable", async () => {
    fetchGithubSummary.mockResolvedValue(summary());
    fetchGithubActivity.mockResolvedValue(null);

    render(await Home());

    // A widget failing must not take out the page, and must not pretend the
    // list is simply empty.
    expect(screen.getByText(/Activity unavailable right now/)).toBeInTheDocument();
    expect(screen.getByText("1,284")).toBeInTheDocument();
  });

  it("falls back quietly when the summary endpoint is unavailable", async () => {
    fetchGithubSummary.mockResolvedValue(null);
    fetchGithubActivity.mockResolvedValue(activity());

    render(await Home());

    expect(screen.getByText(/Stats unavailable right now/)).toBeInTheDocument();
    expect(
      screen.getByText("Add streaming responses to chat endpoint"),
    ).toBeInTheDocument();
  });

  it("still renders the page when GitHub is unavailable entirely", async () => {
    fetchGithubSummary.mockResolvedValue(null);
    fetchGithubActivity.mockResolvedValue(null);

    render(await Home());

    expect(
      screen.getByRole("heading", { level: 2, name: "Currently building" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Activity unavailable right now/)).toBeInTheDocument();
    expect(screen.getByText(/Stats unavailable right now/)).toBeInTheDocument();
  });

  it("links the stats card to the configured GitHub profile", async () => {
    fetchGithubSummary.mockResolvedValue(summary());
    fetchGithubActivity.mockResolvedValue(activity());

    render(await Home());

    const profile = siteConfig.social.find((link) => link.label === "GitHub");
    expect(screen.getByRole("link", { name: "@SaqibCod" })).toHaveAttribute(
      "href",
      profile?.href,
    );
  });
});
