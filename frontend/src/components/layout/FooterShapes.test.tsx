import { fireEvent, render } from "@testing-library/react";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { FooterShapes } from "@/components/layout/FooterShapes";

const POKED = "[transform:rotate(24deg)_scale(1.15)]";

function renderShapes() {
  const { container } = render(<FooterShapes />);
  return [...container.querySelectorAll("svg")];
}

/**
 * Exact token match. A substring check would always pass, because the
 * hover-prefixed utility contains the un-prefixed class as a substring.
 *
 * Keep class-shaped text out of comments in this file — Tailwind scans
 * comments too, and would emit a dead rule for anything that looks like a
 * utility.
 */
function isPoked(element: Element): boolean {
  return (element.getAttribute("class") ?? "").split(/\s+/).includes(POKED);
}

function hasHoverPoke(element: Element): boolean {
  return (element.getAttribute("class") ?? "")
    .split(/\s+/)
    .includes(`hover:${POKED}`);
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("FooterShapes", () => {
  it("renders the eight marks, hidden from assistive tech", () => {
    const shapes = renderShapes();

    expect(shapes).toHaveLength(8);
    expect(shapes[0].closest("[aria-hidden='true']")).not.toBeNull();
  });

  it("staggers the wobble so the row doesn't move in unison", () => {
    const delays = renderShapes().map((s) => s.style.animationDelay);

    expect(delays).toEqual([
      "0s",
      "-1.5s",
      "-3s",
      "-1.5s",
      "0s",
      "-3s",
      "0s",
      "-1.5s",
    ]);
  });

  it("keeps the hover transform for pointer devices", () => {
    expect(hasHoverPoke(renderShapes()[0])).toBe(true);
  });

  it("applies the transform on tap, then releases it", () => {
    const shapes = renderShapes();

    fireEvent.pointerDown(shapes[3], { pointerType: "touch" });
    expect(isPoked(shapes[3])).toBe(true);
    // Only the tapped shape moves.
    expect(isPoked(shapes[2])).toBe(false);

    act(() => void vi.advanceTimersByTime(500));
    expect(isPoked(shapes[3])).toBe(false);
  });

  it("ignores mouse input, which :hover already covers", () => {
    const shapes = renderShapes();

    fireEvent.pointerDown(shapes[0], { pointerType: "mouse" });

    expect(isPoked(shapes[0])).toBe(false);
    expect(hasHoverPoke(shapes[0])).toBe(true);
  });

  it("moves the poke to the newest tap", () => {
    const shapes = renderShapes();

    fireEvent.pointerDown(shapes[1], { pointerType: "touch" });
    act(() => void vi.advanceTimersByTime(200));
    fireEvent.pointerDown(shapes[5], { pointerType: "touch" });

    expect(isPoked(shapes[1])).toBe(false);
    expect(isPoked(shapes[5])).toBe(true);
  });
});
