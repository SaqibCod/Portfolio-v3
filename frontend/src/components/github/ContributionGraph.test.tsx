import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ContributionGraph } from "@/components/github/ContributionGraph";

import type { ContributionDay } from "@/lib/github";

/** `count` days starting at 2026-01-01, each with the count the callback gives. */
function days(count: number, countFor: (index: number) => number): ContributionDay[] {
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(Date.UTC(2026, 0, 1 + index));
    return { date: date.toISOString().slice(0, 10), count: countFor(index) };
  });
}

describe("ContributionGraph", () => {
  it("states the year's total as text", () => {
    render(<ContributionGraph contributions={days(7, () => 1)} totalContributions={1284} />);

    // The headline figure is readable without interpreting the graph at all,
    // and with thousands separated.
    expect(screen.getByText("1,284")).toBeInTheDocument();
    expect(
      screen.getByText("contributions in the last year"),
    ).toBeInTheDocument();
  });

  it("draws one column per week", () => {
    render(<ContributionGraph contributions={days(371, () => 1)} totalContributions={371} />);

    expect(screen.getAllByRole("listitem")).toHaveLength(53);
  });

  it("keeps a final partial week so the newest day is still drawn", () => {
    // 10 days is one full column plus three days; dropping the remainder would
    // silently hide the most recent activity, which is the part people look at.
    render(<ContributionGraph contributions={days(10, () => 1)} totalContributions={10} />);

    const weeks = screen.getAllByRole("listitem");
    expect(weeks).toHaveLength(2);
    expect(weeks[1].childElementCount).toBe(3);
  });

  it("gives every week a text alternative instead of every square", () => {
    render(
      <ContributionGraph
        contributions={days(7, (index) => (index === 0 ? 12 : 0))}
        totalContributions={12}
      />,
    );

    // One label per column, not 371 meaningless stops.
    expect(
      screen.getByRole("listitem", { name: "Week of Jan 1, 2026: 12 contributions" }),
    ).toBeInTheDocument();
  });

  it("says \"1 contribution\" rather than \"1 contributions\"", () => {
    render(
      <ContributionGraph
        contributions={days(7, (index) => (index === 0 ? 1 : 0))}
        totalContributions={1}
      />,
    );

    expect(
      screen.getByRole("listitem", { name: "Week of Jan 1, 2026: 1 contribution" }),
    ).toBeInTheDocument();
  });

  it("labels the grid with how many weeks it covers", () => {
    render(<ContributionGraph contributions={days(371, () => 0)} totalContributions={0} />);

    expect(
      screen.getByRole("list", {
        name: "Contributions per week over the last 53 weeks",
      }),
    ).toBeInTheDocument();
  });

  it("shades each day relative to the busiest one", () => {
    // Busiest is 20, so the four shades fall on the quarter boundaries — 5,
    // 10, 15, 20 — and a zero day keeps the plain hairline background.
    render(
      <ContributionGraph
        contributions={[
          { date: "2026-01-01", count: 0 },
          { date: "2026-01-02", count: 5 },
          { date: "2026-01-03", count: 10 },
          { date: "2026-01-04", count: 15 },
          { date: "2026-01-05", count: 20 },
        ]}
        totalContributions={50}
      />,
    );

    const squares = screen.getAllByRole("listitem")[0].children;
    expect(squares[0]).toHaveClass("bg-line");
    expect(squares[1]).toHaveClass("bg-heat-1");
    expect(squares[2]).toHaveClass("bg-heat-2");
    expect(squares[3]).toHaveClass("bg-heat-3");
    expect(squares[4]).toHaveClass("bg-heat-4");
  });

  it("keeps every square out of the accessibility tree", () => {
    render(<ContributionGraph contributions={days(7, () => 3)} totalContributions={21} />);

    const squares = screen.getAllByRole("listitem")[0].children;
    for (const square of squares) {
      expect(square).toHaveAttribute("aria-hidden", "true");
    }
  });

  it("renders a graph of a year with no contributions at all", () => {
    render(<ContributionGraph contributions={days(371, () => 0)} totalContributions={0} />);

    expect(screen.getByText("0")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(53);
  });
});
