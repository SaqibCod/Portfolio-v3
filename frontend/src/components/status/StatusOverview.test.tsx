import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { StatusOverview } from "@/components/status/StatusOverview";

import type {
  ServiceState,
  ServiceStatus,
  StatusHistory,
  StatusOverview as StatusOverviewData,
} from "@/lib/status";

const fetchLiveStatusOverview = vi.hoisted(() => vi.fn());
const fetchStatusHistory = vi.hoisted(() => vi.fn());

vi.mock("@/lib/status", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/status")>()),
  fetchLiveStatusOverview,
  fetchStatusHistory,
}));

function service(
  slug: string,
  name: string,
  state: ServiceState,
  uptime = 99.82,
): ServiceStatus {
  return {
    slug,
    name,
    url: `https://${slug}.example.com`,
    state,
    responseTimeMs: state === "down" || state === "unknown" ? null : 182,
    uptime30d: uptime,
  };
}

function overview(
  overall: ServiceState,
  services: ServiceStatus[],
): StatusOverviewData {
  return { overall, services, checkedAt: "2026-11-22T10:35:00Z" };
}

function history(slug: string, points = 30): StatusHistory {
  return {
    slug,
    window: "30d",
    uptime: 99.82,
    points: Array.from({ length: points }, (_, index) => ({
      timestamp: new Date(1764000000000 + index * 86400000).toISOString(),
      state: "operational" as const,
      avgResponseTimeMs: 180 + index,
    })),
  };
}

function renderOverview(data: StatusOverviewData | null) {
  const summaries = Object.fromEntries(
    (data?.services ?? []).map((entry) => [entry.slug, history(entry.slug)]),
  );

  return render(
    <StatusOverview
      initialOverview={data}
      summaries={summaries}
      caseStudies={{ githmind: "/projects/githmind" }}
    />,
  );
}

beforeEach(() => {
  fetchLiveStatusOverview.mockResolvedValue(null);
  fetchStatusHistory.mockResolvedValue(null);
  window.history.replaceState(null, "", "/status");
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("StatusOverview banner", () => {
  it("leads with the overall state in words", () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    expect(
      screen.getByRole("heading", { name: "All Systems Operational" }),
    ).toBeInTheDocument();
  });

  it("names the affected project when something is degraded", () => {
    renderOverview(
      overview("degraded", [
        service("githmind", "githMind", "operational"),
        service("medi-scan", "Medi-Scan", "degraded"),
      ]),
    );

    expect(
      screen.getByRole("heading", { name: "Partial Degradation" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Medi-Scan is responding slowly/)).toBeInTheDocument();
  });

  it("names the project that is down", () => {
    renderOverview(
      overview("down", [
        service("githmind", "githMind", "operational"),
        service("medi-scan", "Medi-Scan", "down"),
      ]),
    );

    expect(
      screen.getByRole("heading", { name: "Medi-Scan Is Down" }),
    ).toBeInTheDocument();
  });

  it("announces itself politely, since it changes while the page is open", () => {
    const { container } = renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    expect(container.querySelector('[role="status"]')).toHaveAttribute(
      "aria-live",
      "polite",
    );
  });

  it("renders an absolute UTC time first, so the server and browser agree", () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    // Hydration-safe: the relative wording is swapped in after mounting, which
    // in a test environment has already happened by the time this is queried.
    expect(screen.getByText(/^Checked/)).toBeInTheDocument();
  });

  it("legends all four states, each with a word", () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );
    const legend = screen.getByRole("list", { name: "Legend" });

    expect(within(legend).getByText("Operational")).toBeInTheDocument();
    expect(within(legend).getByText("Degraded")).toBeInTheDocument();
    expect(within(legend).getByText("Down")).toBeInTheDocument();
    expect(within(legend).getByText("Unknown / no data")).toBeInTheDocument();
  });

  it("re-reads the API when Refresh is pressed", async () => {
    fetchLiveStatusOverview.mockResolvedValue(
      overview("down", [service("githmind", "githMind", "down")]),
    );
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    await userEvent.click(screen.getByRole("button", { name: /Refresh/ }));

    expect(fetchLiveStatusOverview).toHaveBeenCalled();
    expect(
      await screen.findByRole("heading", { name: "githMind Is Down" }),
    ).toBeInTheDocument();
  });

  it("keeps the figures on screen when a refresh fails", async () => {
    // A failed poll must not blank a page that is already showing real data.
    fetchLiveStatusOverview.mockResolvedValue(null);
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    await userEvent.click(screen.getByRole("button", { name: /Refresh/ }));

    expect(
      screen.getByRole("heading", { name: "All Systems Operational" }),
    ).toBeInTheDocument();
  });
});

describe("StatusOverview services", () => {
  it("names each service and states it in words, not only in colour", () => {
    // The acceptance criterion. Scoped to the rows rather than the whole page,
    // because the legend lists all four words too.
    renderOverview(
      overview("degraded", [
        service("githmind", "githMind", "operational"),
        service("medi-scan", "Medi-Scan", "degraded"),
      ]),
    );
    const rows = screen.getAllByRole("article");

    expect(within(rows[0]).getByText("githMind")).toBeInTheDocument();
    expect(
      within(rows[0]).getByText(/^Operational/),
    ).toBeInTheDocument();
    expect(within(rows[1]).getByText("Medi-Scan")).toBeInTheDocument();
    expect(within(rows[1]).getByText(/^Degraded/)).toBeInTheDocument();
  });

  it("shows each service's host and uptime", () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    expect(
      screen.getByText(/githmind\.example\.com/),
    ).toBeInTheDocument();
    expect(screen.getByText("99.82%")).toBeInTheDocument();
  });

  it("dashes the uptime of a service nobody has probed", () => {
    // Rather than "0%", which would read as a month of downtime. Both the
    // response time and the uptime dash, hence the plural query.
    renderOverview(
      overview("unknown", [service("githmind", "githMind", "unknown", 0)]),
    );

    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
    expect(screen.queryByText("0%")).not.toBeInTheDocument();
  });

  it("opens the first service by default, as the design shows", () => {
    renderOverview(
      overview("operational", [
        service("githmind", "githMind", "operational"),
        service("medi-scan", "Medi-Scan", "operational"),
      ]),
    );
    const rows = screen.getAllByRole("button", { expanded: false });

    // One of the two is expanded, so only one collapsed row remains.
    expect(rows).toHaveLength(1);
  });

  it("closes an open row when its header is pressed", async () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );
    const row = screen.getByRole("button", { expanded: true });

    await userEvent.click(row);

    expect(screen.getByRole("button", { expanded: false })).toBeInTheDocument();
  });

  it("mirrors the open row into the URL so a link can share it", async () => {
    renderOverview(
      overview("operational", [
        service("githmind", "githMind", "operational"),
        service("medi-scan", "Medi-Scan", "operational"),
      ]),
    );

    await userEvent.click(screen.getByRole("button", { expanded: false }));

    expect(window.location.search).toContain("service=");
    expect(window.location.search).toContain("medi-scan");
  });

  it("restores the window from the URL", () => {
    window.history.replaceState(null, "", "/status?window=7d");

    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    expect(
      screen.getByRole("button", { name: "7d", pressed: true }),
    ).toBeInTheDocument();
  });

  it("falls back to 24h when the URL names a window the contract lacks", () => {
    window.history.replaceState(null, "", "/status?window=90d");

    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    expect(
      screen.getByRole("button", { name: "24h", pressed: true }),
    ).toBeInTheDocument();
  });

  it("fetches a window the server did not send, once", async () => {
    fetchStatusHistory.mockResolvedValue({
      slug: "githmind",
      window: "7d",
      uptime: 98.5,
      points: [],
    } satisfies StatusHistory);

    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    await userEvent.click(screen.getByRole("button", { name: "7d" }));

    expect(fetchStatusHistory).toHaveBeenCalledWith("githmind", "7d");
  });

  it("reuses the server's 30-day history rather than re-fetching it", async () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    await userEvent.click(screen.getByRole("button", { name: "30d" }));

    // The default 24h window is fetched, since the server only sent 30d for the
    // row strip — but 30d itself is served from what is already in the page.
    expect(fetchStatusHistory).not.toHaveBeenCalledWith("githmind", "30d");
  });

  it("asks for each window only once", async () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    await userEvent.click(screen.getByRole("button", { name: "7d" }));
    await userEvent.click(screen.getByRole("button", { name: "24h" }));
    await userEvent.click(screen.getByRole("button", { name: "7d" }));

    const sevenDayCalls = fetchStatusHistory.mock.calls.filter(
      ([, window]) => window === "7d",
    );
    expect(sevenDayCalls).toHaveLength(1);
  });

  it("offers the case study only for a project that has one", () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    expect(
      screen.getByRole("link", { name: "Case Study" }),
    ).toHaveAttribute("href", "/projects/githmind");
  });

  it("links through to the project itself", () => {
    renderOverview(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );

    expect(
      screen.getByRole("link", { name: "Open githMind" }),
    ).toHaveAttribute("href", "https://githmind.example.com");
  });
});

describe("StatusOverview with no monitor", () => {
  it("explains the outage instead of claiming everything is fine", () => {
    renderOverview(null);

    expect(
      screen.getByRole("heading", { name: "Status Unavailable" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Can’t Reach the Monitor" }),
    ).toBeInTheDocument();
  });

  it("says plainly that the projects may still be up", () => {
    renderOverview(null);

    expect(
      screen.getByText(/doesn’t necessarily mean the projects are down/),
    ).toBeInTheDocument();
  });

  it("raises the panel as an alert", () => {
    const { container } = renderOverview(null);

    expect(container.querySelector('[role="alert"]')).toBeInTheDocument();
  });

  it("offers a retry that goes back to the API", async () => {
    renderOverview(null);

    await userEvent.click(screen.getByRole("button", { name: "Try Again" }));

    expect(fetchLiveStatusOverview).toHaveBeenCalled();
  });

  it("recovers on its own once the API answers", async () => {
    fetchLiveStatusOverview.mockResolvedValue(
      overview("operational", [service("githmind", "githMind", "operational")]),
    );
    renderOverview(null);

    await userEvent.click(screen.getByRole("button", { name: "Try Again" }));

    expect(
      await screen.findByRole("heading", { name: "All Systems Operational" }),
    ).toBeInTheDocument();
  });
});
