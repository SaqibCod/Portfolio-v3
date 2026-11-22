import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { StatusChip } from "@/components/status/StatusChip";

import type { ServiceState, StatusOverview } from "@/lib/status";

const fetchStatusOverview = vi.hoisted(() => vi.fn());

vi.mock("@/lib/status", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/status")>()),
  fetchStatusOverview,
}));

function overview(overall: ServiceState): StatusOverview {
  return {
    overall,
    services: [
      {
        slug: "githmind",
        name: "githMind",
        url: "https://githmind.example.com",
        state: overall,
        responseTimeMs: 182,
        uptime30d: 99.82,
      },
    ],
    checkedAt: "2026-11-22T10:35:00Z",
  };
}

beforeEach(() => {
  fetchStatusOverview.mockResolvedValue(null);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("StatusChip", () => {
  it("renders nothing while it has no answer", () => {
    const { container } = render(<StatusChip />);

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when the API is unreachable", async () => {
    // A stale or invented health claim in the footer of every page would be
    // worse than no chip at all.
    const { container } = render(<StatusChip />);

    await vi.waitFor(() => expect(fetchStatusOverview).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when no project is monitored", async () => {
    // Which is how the chip publishes itself from the backend's configuration:
    // it appears with the status page and disappears with it.
    fetchStatusOverview.mockResolvedValue({
      overall: "unknown",
      services: [],
      checkedAt: "2026-11-22T10:35:00Z",
    } satisfies StatusOverview);

    const { container } = render(<StatusChip />);

    await vi.waitFor(() => expect(fetchStatusOverview).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("links through to the status page", async () => {
    fetchStatusOverview.mockResolvedValue(overview("operational"));

    render(<StatusChip />);

    expect(
      await screen.findByRole("link", { name: "All systems operational" }),
    ).toHaveAttribute("href", "/status");
  });

  it("labels every state the contract allows", async () => {
    const cases = [
      ["operational", "All systems operational"],
      ["degraded", "Degraded performance"],
      ["down", "Service disruption"],
      ["unknown", "Status unknown"],
    ] as const;

    for (const [state, label] of cases) {
      fetchStatusOverview.mockResolvedValue(overview(state));
      const { unmount } = render(<StatusChip />);

      expect(await screen.findByRole("link", { name: label })).toBeInTheDocument();
      unmount();
    }
  });

  it("pulses only while healthy", async () => {
    fetchStatusOverview.mockResolvedValue(overview("operational"));
    const { container: ok, unmount } = render(<StatusChip />);
    await screen.findByRole("link");
    expect(ok.querySelector("i")?.className).toContain("dot-pulse");
    unmount();

    fetchStatusOverview.mockResolvedValue(overview("down"));
    const { container: bad } = render(<StatusChip />);
    await screen.findByRole("link");
    expect(bad.querySelector("i")?.className).not.toContain("dot-pulse");
  });

  it("colours the dot by state", async () => {
    fetchStatusOverview.mockResolvedValue(overview("degraded"));

    const { container } = render(<StatusChip />);
    await screen.findByRole("link");

    expect(container.querySelector("i")?.className).toContain("bg-warn");
  });
});
