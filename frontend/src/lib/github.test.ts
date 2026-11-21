import { describe, expect, it } from "vitest";

import {
  busiestDay,
  heatLevel,
  toContributionWeeks,
  type ContributionDay,
} from "@/lib/github";

function days(counts: number[]): ContributionDay[] {
  return counts.map((count, index) => {
    const date = new Date(Date.UTC(2026, 0, 1 + index));
    return { date: date.toISOString().slice(0, 10), count };
  });
}

describe("toContributionWeeks", () => {
  it("chunks the flat day list into columns of seven", () => {
    const weeks = toContributionWeeks(days(Array.from({ length: 14 }, () => 1)));

    expect(weeks).toHaveLength(2);
    expect(weeks[0].days).toHaveLength(7);
    expect(weeks[1].days).toHaveLength(7);
  });

  it("keeps the days in the order they arrived", () => {
    const weeks = toContributionWeeks(days([1, 2, 3, 4, 5, 6, 7, 8]));

    expect(weeks[0].startDate).toBe("2026-01-01");
    expect(weeks[1].startDate).toBe("2026-01-08");
  });

  it("totals each column", () => {
    const weeks = toContributionWeeks(days([1, 2, 3, 0, 0, 4, 5]));

    expect(weeks[0].total).toBe(15);
  });

  it("keeps a trailing partial week", () => {
    // The last column is usually partial — this week — and it holds the most
    // recent activity, so dropping it would hide exactly the interesting part.
    const weeks = toContributionWeeks(days([1, 1, 1, 1, 1, 1, 1, 1, 1]));

    expect(weeks).toHaveLength(2);
    expect(weeks[1].days).toHaveLength(2);
  });

  it("returns nothing for an empty year", () => {
    expect(toContributionWeeks([])).toEqual([]);
  });
});

describe("busiestDay", () => {
  it("finds the highest single-day count", () => {
    expect(busiestDay(days([3, 19, 0, 7]))).toBe(19);
  });

  it("is zero for a year with no contributions", () => {
    expect(busiestDay(days([0, 0, 0]))).toBe(0);
  });

  it("is zero for no days at all", () => {
    expect(busiestDay([])).toBe(0);
  });
});

describe("heatLevel", () => {
  it("leaves a day with no contributions unshaded", () => {
    expect(heatLevel(0, 20)).toBe(0);
  });

  it("shades relative to the busiest day, not to a fixed count", () => {
    // The same count means a different shade for a busy year and a quiet one,
    // which is the point: the graph is a shape to read, not a measurement.
    expect(heatLevel(5, 20)).toBe(1);
    expect(heatLevel(5, 5)).toBe(4);
  });

  it("puts the four shades on the quarter boundaries", () => {
    expect(heatLevel(5, 20)).toBe(1);
    expect(heatLevel(10, 20)).toBe(2);
    expect(heatLevel(15, 20)).toBe(3);
    expect(heatLevel(20, 20)).toBe(4);
  });

  it("gives a single contribution in a busy year the lightest shade", () => {
    // Never 0: a day something happened on must not look like a day nothing
    // happened on, however quiet it was.
    expect(heatLevel(1, 500)).toBe(1);
  });

  it("treats an impossible busiest day as nothing to scale against", () => {
    expect(heatLevel(3, 0)).toBe(0);
  });
});
