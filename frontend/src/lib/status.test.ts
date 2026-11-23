import { describe, expect, it } from "vitest";

import {
  averageResponseTime,
  bannerCopy,
  BUCKET_COUNTS,
  countStates,
  describeStates,
  formatCheckedAtUtc,
  formatResponseTime,
  formatUptime,
  hostFromUrl,
  sparklinePoints,
  SPARKLINE_VIEWBOX,
  type ServiceState,
  type ServiceStatus,
  type StatusOverview,
  type StatusPoint,
} from "@/lib/status";

function service(
  slug: string,
  name: string,
  state: ServiceState,
  uptime = 100,
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
): StatusOverview {
  return { overall, services, checkedAt: "2026-11-22T10:35:00Z" };
}

function point(state: ServiceState, avgResponseTimeMs: number | null = null): StatusPoint {
  return {
    // Unique per call so React keys and assertions stay distinguishable.
    timestamp: new Date(1764000000000 + counter++ * 300000).toISOString(),
    state,
    avgResponseTimeMs,
  };
}

let counter = 0;

describe("BUCKET_COUNTS", () => {
  it("matches the counts the contract documents", () => {
    expect(BUCKET_COUNTS).toEqual({ "24h": 288, "7d": 168, "30d": 30 });
  });
});

describe("hostFromUrl", () => {
  it("strips the scheme and path", () => {
    expect(hostFromUrl("https://githmind.example.com/health")).toBe(
      "githmind.example.com",
    );
  });

  it("keeps a port, which distinguishes two local services", () => {
    expect(hostFromUrl("http://localhost:3000")).toBe("localhost:3000");
  });

  it("falls back to the raw value rather than throwing", () => {
    expect(hostFromUrl("not a url")).toBe("not a url");
  });

  it("is empty for a service with no URL", () => {
    expect(hostFromUrl(undefined)).toBe("");
  });
});

describe("formatResponseTime", () => {
  it("formats a measured round trip", () => {
    expect(formatResponseTime(182, "operational")).toBe("182\u00a0ms");
  });

  it("groups thousands", () => {
    expect(formatResponseTime(2400, "degraded")).toBe("2,400\u00a0ms");
  });

  it("says timeout for a service that did not answer", () => {
    // More useful than a dash: the probe ran, it just got nothing back.
    expect(formatResponseTime(null, "down")).toBe("timeout");
  });

  it("dashes a service nobody has measured", () => {
    expect(formatResponseTime(null, "unknown")).toBe("—");
  });
});

describe("formatUptime", () => {
  it("drops trailing zeroes", () => {
    expect(formatUptime(100)).toBe("100%");
    expect(formatUptime(99.8)).toBe("99.8%");
  });

  it("keeps two decimals when they mean something", () => {
    expect(formatUptime(99.82)).toBe("99.82%");
  });

  it("dashes an unprobed service rather than claiming zero", () => {
    // 0% would read as a month of downtime instead of an absence of data.
    expect(formatUptime(0, "unknown")).toBe("—");
  });

  it("still shows a real zero for a service that was down all month", () => {
    expect(formatUptime(0, "down")).toBe("0%");
  });
});

describe("formatCheckedAtUtc", () => {
  it("is a fixed-zone wall clock, so the server and browser agree", () => {
    expect(formatCheckedAtUtc("2026-11-22T10:35:00Z")).toBe("10:35 UTC");
  });

  it("does not shift with the machine's offset", () => {
    expect(formatCheckedAtUtc("2026-11-22T23:05:00Z")).toBe("23:05 UTC");
  });
});

describe("bannerCopy", () => {
  it("names the count when everything is healthy", () => {
    const copy = bannerCopy(
      overview("operational", [
        service("a", "A", "operational"),
        service("b", "B", "operational"),
        service("c", "C", "operational"),
      ]),
    );

    expect(copy.title).toBe("All Systems Operational");
    expect(copy.detail).toBe("All 3 services are up and responding normally.");
  });

  it("speaks in the singular for a single service", () => {
    const copy = bannerCopy(
      overview("operational", [service("a", "githMind", "operational")]),
    );

    expect(copy.detail).toBe("githMind is up and responding normally.");
  });

  it("names the slow service rather than saying 'a service'", () => {
    const copy = bannerCopy(
      overview("degraded", [
        service("a", "githMind", "operational"),
        service("b", "Medi-Scan", "degraded"),
      ]),
    );

    expect(copy.title).toBe("Partial Degradation");
    expect(copy.detail).toContain("Medi-Scan");
    expect(copy.detail).toContain("Everything else is fine.");
  });

  it("names the service that is down", () => {
    const copy = bannerCopy(
      overview("down", [
        service("a", "githMind", "operational"),
        service("b", "Medi-Scan", "down"),
      ]),
    );

    expect(copy.title).toBe("Medi-Scan Is Down");
    expect(copy.detail).toContain("The other services are unaffected.");
  });

  it("counts rather than lists when several are down", () => {
    const copy = bannerCopy(
      overview("down", [
        service("a", "githMind", "down"),
        service("b", "Medi-Scan", "down"),
      ]),
    );

    expect(copy.title).toBe("2 Services Are Down");
    expect(copy.detail).toContain("Every monitored service is affected.");
  });

  it("explains an all-unknown overview as not yet probed", () => {
    const copy = bannerCopy(
      overview("unknown", [service("a", "githMind", "unknown")]),
    );

    expect(copy.title).toBe("Status Unknown");
    expect(copy.detail).toBe(
      "Nothing has been probed yet, so there is no state to report.",
    );
  });

  it("names which service is unprobed when the others are fine", () => {
    const copy = bannerCopy(
      overview("unknown", [
        service("a", "githMind", "operational"),
        service("b", "Medi-Scan", "unknown"),
      ]),
    );

    expect(copy.detail).toBe("Medi-Scan has not been probed yet.");
  });
});

describe("countStates", () => {
  it("counts every state, including the ones that never occur", () => {
    const counts = countStates([
      point("operational"),
      point("operational"),
      point("down"),
    ]);

    expect(counts).toEqual({
      operational: 2,
      degraded: 0,
      down: 1,
      unknown: 0,
    });
  });
});

describe("describeStates", () => {
  it("states the window's shape in words", () => {
    // This is what makes the bar chart readable without seeing colour.
    const described = describeStates([
      point("operational"),
      point("operational"),
      point("degraded"),
      point("down"),
    ]);

    expect(described).toBe("2 operational · 1 degraded · 1 down");
  });

  it("leaves out states that did not occur", () => {
    expect(describeStates([point("operational")])).toBe("1 operational");
  });

  it("says so when there is nothing at all", () => {
    expect(describeStates([])).toBe("No data");
  });
});

describe("averageResponseTime", () => {
  it("averages only the buckets that measured something", () => {
    const average = averageResponseTime([
      point("operational", 100),
      point("down", null),
      point("operational", 300),
    ]);

    expect(average).toBe(200);
  });

  it("is null when nothing was measured", () => {
    expect(averageResponseTime([point("down", null)])).toBeNull();
    expect(averageResponseTime([])).toBeNull();
  });
});

describe("sparklinePoints", () => {
  it("needs two measurements before it draws anything", () => {
    expect(sparklinePoints([])).toBeNull();
    expect(sparklinePoints([point("operational", 100)])).toBeNull();
  });

  it("plots one coordinate per measured bucket", () => {
    const line = sparklinePoints([
      point("operational", 100),
      point("operational", 200),
      point("operational", 300),
    ]);

    expect(line?.split(" ")).toHaveLength(3);
  });

  it("skips unmeasured buckets instead of plotting them at zero", () => {
    // Plotting a failed probe at the axis would draw a cliff that never
    // happened; the uptime strip above is where an outage is shown.
    const line = sparklinePoints([
      point("operational", 100),
      point("down", null),
      point("operational", 100),
    ]);

    expect(line?.split(" ")).toHaveLength(2);
  });

  it("puts the slowest bucket highest and stays inside the viewBox", () => {
    const line = sparklinePoints([
      point("operational", 100),
      point("operational", 1000),
    ]);
    const ys = line!.split(" ").map((pair) => Number(pair.split(",")[1]));

    expect(ys[1]).toBeLessThan(ys[0]);
    for (const y of ys) {
      expect(y).toBeGreaterThanOrEqual(0);
      expect(y).toBeLessThanOrEqual(SPARKLINE_VIEWBOX.height);
    }
  });

  it("spans the full width using the bucket's position, not its index", () => {
    // A measurement in the last bucket has to land at the right edge even when
    // every bucket before it failed.
    const line = sparklinePoints([
      point("operational", 100),
      point("down", null),
      point("operational", 200),
    ]);
    const xs = line!.split(" ").map((pair) => Number(pair.split(",")[0]));

    expect(xs[0]).toBe(0);
    expect(xs[1]).toBe(SPARKLINE_VIEWBOX.width);
  });
});
