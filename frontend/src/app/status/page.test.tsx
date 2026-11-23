import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { siteConfig } from "@/lib/site-config";

import StatusPage, { metadata, revalidate } from "./page";

import type { ServiceState, StatusHistory, StatusOverview } from "@/lib/status";

const fetchStatusOverview = vi.hoisted(() => vi.fn());
const fetchStatusHistory = vi.hoisted(() => vi.fn());
const fetchLiveStatusOverview = vi.hoisted(() => vi.fn());
const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
);

vi.mock("@/lib/status", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/status")>()),
  fetchStatusOverview,
  fetchStatusHistory,
  fetchLiveStatusOverview,
}));

vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  notFound,
}));

function overview(
  overall: ServiceState,
  slugs: string[] = ["githmind"],
): StatusOverview {
  return {
    overall,
    services: slugs.map((slug) => ({
      slug,
      name: slug,
      url: `https://${slug}.example.com`,
      state: overall,
      responseTimeMs: 182,
      uptime30d: 99.82,
    })),
    checkedAt: "2026-11-22T10:35:00Z",
  };
}

const emptyHistory: StatusHistory = {
  slug: "githmind",
  window: "30d",
  uptime: 99.82,
  points: [],
};

beforeEach(() => {
  fetchStatusHistory.mockResolvedValue(emptyHistory);
  fetchLiveStatusOverview.mockResolvedValue(null);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("Status page metadata", () => {
  it("carries a title, description and canonical", () => {
    expect(metadata.title).toBe("Status");
    expect(metadata.description).toBeTruthy();
    expect(metadata.alternates?.canonical).toBe("/status");
  });

  it("names the site in its Open Graph URL", () => {
    expect(metadata.openGraph?.url).toBe(`${siteConfig.siteUrl}/status`);
  });

  it("is revalidated once a minute, matching the probe cadence", () => {
    expect(revalidate).toBe(60);
  });
});

describe("Status page", () => {
  it("does not exist when nothing is monitored", async () => {
    // The configuration the site ships in. A status page listing nothing is
    // worse than no status page, so the route 404s rather than rendering empty.
    fetchStatusOverview.mockResolvedValue(overview("unknown", []));

    await expect(StatusPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(notFound).toHaveBeenCalled();
  });

  it("asks for no history when nothing is monitored", async () => {
    fetchStatusOverview.mockResolvedValue(overview("unknown", []));

    await expect(StatusPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(fetchStatusHistory).not.toHaveBeenCalled();
  });

  it("still renders when the API is unreachable", async () => {
    // "Nothing is monitored" is a statement; "the monitor is down" is the
    // absence of one. Only the first un-publishes the page.
    fetchStatusOverview.mockResolvedValue(null);

    render(await StatusPage());

    expect(notFound).not.toHaveBeenCalled();
    expect(
      screen.getByRole("heading", { level: 1, name: "Status" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Can’t Reach the Monitor" }),
    ).toBeInTheDocument();
  });

  it("renders one heading and the monitored services", async () => {
    fetchStatusOverview.mockResolvedValue(
      overview("operational", ["githmind", "medi-scan"]),
    );

    render(await StatusPage());

    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getAllByRole("article")).toHaveLength(2);
  });

  it("fetches the 30-day history for every service, in parallel", async () => {
    fetchStatusOverview.mockResolvedValue(
      overview("operational", ["githmind", "medi-scan"]),
    );

    render(await StatusPage());

    expect(fetchStatusHistory).toHaveBeenCalledWith("githmind", "30d");
    expect(fetchStatusHistory).toHaveBeenCalledWith("medi-scan", "30d");
  });

  it("explains the three documented bucket sizes", async () => {
    fetchStatusOverview.mockResolvedValue(overview("operational"));

    render(await StatusPage());

    expect(screen.getByText(/5-minute buckets over 24 hours/)).toBeInTheDocument();
    expect(screen.getByText(/hourly over 7 days/)).toBeInTheDocument();
    expect(screen.getByText(/daily over 30/)).toBeInTheDocument();
  });
});
