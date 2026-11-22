import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { UptimeBars } from "@/components/status/UptimeBars";

import type { ServiceState, StatusPoint } from "@/lib/status";

let counter = 0;

function point(
  state: ServiceState,
  avgResponseTimeMs: number | null = null,
): StatusPoint {
  return {
    timestamp: new Date(1764000000000 + counter++ * 300000).toISOString(),
    state,
    avgResponseTimeMs,
  };
}

describe("UptimeBars", () => {
  it("draws one bar per bucket", () => {
    const { container } = render(
      <UptimeBars
        points={[point("operational"), point("degraded"), point("down")]}
        label="githMind uptime"
      />,
    );

    expect(container.querySelectorAll("i")).toHaveLength(3);
  });

  it("colours each bar by its state", () => {
    const { container } = render(
      <UptimeBars
        points={[
          point("operational"),
          point("degraded"),
          point("down"),
          point("unknown"),
        ]}
        label="githMind uptime"
      />,
    );
    const classes = [...container.querySelectorAll("i")].map(
      (bar) => bar.className,
    );

    expect(classes[0]).toContain("bg-ok");
    expect(classes[1]).toContain("bg-warn");
    expect(classes[2]).toContain("bg-err");
    expect(classes[3]).toContain("bg-off");
  });

  it("fades an unprobed bucket as well as recolouring it", () => {
    // Two signals, so a gap reads as absent rather than as a fourth result.
    const { container } = render(
      <UptimeBars points={[point("unknown")]} label="githMind uptime" />,
    );

    expect(container.querySelector("i")?.className).toContain("opacity-45");
  });

  it("states the window's shape in words, not only in colour", () => {
    // The acceptance criterion: readable without relying on colour.
    render(
      <UptimeBars
        points={[point("operational"), point("operational"), point("down")]}
        label="githMind uptime over the last 24h"
      />,
    );

    expect(
      screen.getByRole("img", {
        name: "githMind uptime over the last 24h: 2 operational · 1 down",
      }),
    ).toBeInTheDocument();
  });

  it("labels each bar with its time, state and speed", () => {
    const { container } = render(
      <UptimeBars points={[point("degraded", 2400)]} label="githMind uptime" />,
    );

    expect(container.querySelector("i")).toHaveAttribute(
      "title",
      expect.stringContaining("Degraded"),
    );
    expect(container.querySelector("i")?.getAttribute("title")).toContain(
      "2400 ms",
    );
  });

  it("leaves the speed off a bucket that measured nothing", () => {
    const { container } = render(
      <UptimeBars points={[point("down", null)]} label="githMind uptime" />,
    );

    expect(container.querySelector("i")?.getAttribute("title")).toContain(
      "Down",
    );
    expect(container.querySelector("i")?.getAttribute("title")).not.toContain(
      "ms",
    );
  });

  it("hides 288 bars from assistive tech behind one label", () => {
    // Otherwise a screen reader stops 288 times on the way past a picture.
    const points = Array.from({ length: 288 }, () => point("operational"));
    const { container } = render(
      <UptimeBars points={points} label="githMind uptime" size="chart" />,
    );

    for (const bar of container.querySelectorAll("i")) {
      expect(bar).toHaveAttribute("aria-hidden", "true");
    }
    expect(screen.getAllByRole("img")).toHaveLength(1);
  });

  it("says so when there is no history at all", () => {
    render(<UptimeBars points={[]} label="githMind uptime" />);

    expect(
      screen.getByRole("img", { name: "githMind uptime: no data" }),
    ).toBeInTheDocument();
  });

  it("disappears from the accessibility tree when decorative", () => {
    // Which is how it is used inside a row's disclosure button, whose own name
    // already carries the uptime figure the strip illustrates.
    render(
      <UptimeBars
        points={[point("operational")]}
        label="githMind uptime"
        decorative
      />,
    );

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("is decorative when empty too", () => {
    render(<UptimeBars points={[]} label="githMind uptime" decorative />);

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("draws the chart taller than the row summary", () => {
    const { container: strip } = render(
      <UptimeBars points={[point("operational")]} label="a" />,
    );
    const { container: chart } = render(
      <UptimeBars points={[point("operational")]} label="b" size="chart" />,
    );

    expect(strip.firstElementChild?.className).toContain("h-8");
    expect(chart.firstElementChild?.className).toContain("h-14");
  });
});
