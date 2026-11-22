import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { CurrentlyBuilding } from "@/components/github/CurrentlyBuilding";

import type { GithubActivityItem } from "@/lib/github";

const NOW = new Date("2026-11-21T12:00:00Z");

function item(overrides: Partial<GithubActivityItem> = {}): GithubActivityItem {
  return {
    type: "commit",
    repo: "githmind",
    title: "Add streaming responses to chat endpoint",
    url: "https://github.com/SaqibCod/githmind/commit/abc",
    occurredAt: "2026-11-21T10:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  // The relative times are computed from the clock, so it has to be fixed.
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("CurrentlyBuilding", () => {
  it("renders one row per item", () => {
    render(
      <CurrentlyBuilding
        items={[
          item(),
          item({
            type: "pull_request",
            repo: "medi-scan",
            title: "Fall back to Ollama when Gemini times out",
            url: "https://github.com/SaqibCod/medi-scan/pull/12",
          }),
        ]}
      />,
    );

    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(
      screen.getByText("Add streaming responses to chat endpoint"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Fall back to Ollama when Gemini times out"),
    ).toBeInTheDocument();
  });

  it("links each item to the thing it describes", () => {
    render(<CurrentlyBuilding items={[item()]} />);

    expect(
      screen.getByRole("link", { name: "Add streaming responses to chat endpoint" }),
    ).toHaveAttribute("href", "https://github.com/SaqibCod/githmind/commit/abc");
  });

  it("names the repository and the kind of activity", () => {
    render(<CurrentlyBuilding items={[item({ type: "pull_request" })]} />);

    // "pull request", not the contract's "pull_request".
    expect(screen.getByText("githmind · pull request")).toBeInTheDocument();
  });

  it("spells out every kind of activity", () => {
    render(
      <CurrentlyBuilding
        items={[
          item({ type: "commit", url: "https://example.com/c" }),
          item({ type: "pull_request", url: "https://example.com/p" }),
          item({ type: "release", url: "https://example.com/r" }),
        ]}
      />,
    );

    expect(screen.getByText("githmind · commit")).toBeInTheDocument();
    expect(screen.getByText("githmind · pull request")).toBeInTheDocument();
    expect(screen.getByText("githmind · release")).toBeInTheDocument();
  });

  it("shows a compact relative time with the exact moment in the markup", () => {
    render(<CurrentlyBuilding items={[item({ occurredAt: "2026-11-19T12:00:00Z" })]} />);

    const time = screen.getByText("2d ago");
    expect(time.tagName).toBe("TIME");
    // The machine-readable value is exact even though the text is rounded.
    expect(time).toHaveAttribute("datetime", "2026-11-19T12:00:00Z");
  });

  it("says when the data was last read from GitHub", () => {
    render(
      <CurrentlyBuilding items={[item()]} updatedAt="2026-11-21T09:00:00Z" />,
    );

    expect(screen.getByText(/updated 3h ago/)).toBeInTheDocument();
  });

  it("says nothing about freshness when there is no timestamp", () => {
    render(<CurrentlyBuilding items={[item()]} />);

    expect(screen.queryByText(/updated/)).not.toBeInTheDocument();
  });

  it("renders nothing at all for an empty list", () => {
    // The caller owns the fallback wording, because only it knows whether the
    // list is empty because GitHub failed or because nothing was pushed.
    const { container } = render(<CurrentlyBuilding items={[]} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("keeps the row icons out of the accessibility tree", () => {
    render(<CurrentlyBuilding items={[item()]} />);

    // The kind is already written out beside the repository name.
    const icon = screen.getByRole("listitem").querySelector("svg");
    expect(icon).toHaveAttribute("aria-hidden", "true");
  });
});
