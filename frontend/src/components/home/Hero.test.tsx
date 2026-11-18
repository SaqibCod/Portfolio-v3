import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { siteConfig } from "@/lib/site-config";

import { Hero } from "./Hero";

/** Stands in for the OS setting the avatar loop honours. */
function mockReducedMotion(reduce: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: reduce && query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      onchange: null,
      dispatchEvent: vi.fn(),
    })),
  );
}

/** The portrait currently faded in, by file name. */
function visibleFace(container: HTMLElement): string | undefined {
  const shown = Array.from(container.querySelectorAll("img")).find((img) =>
    img.className.includes("opacity-100"),
  );

  return shown?.getAttribute("src")?.match(/avatar-[a-z-]+\.png/)?.[0];
}

beforeEach(() => mockReducedMotion(false));
afterEach(() => vi.unstubAllGlobals());

describe("the hero", () => {
  it("names the person in the only level-one heading", () => {
    render(<Hero />);

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent(`Hi, I’m ${siteConfig.name}`);
  });

  it("exposes the portrait as one labelled button", () => {
    render(<Hero />);

    // A single accessible name for the whole stack — the four portraits
    // inside it are decorative.
    const button = screen.getByRole("button", {
      name: new RegExp(`Portrait of ${siteConfig.name}`),
    });
    expect(button).toBeInTheDocument();
    expect(screen.queryAllByRole("img")).toHaveLength(0);
  });

  it("renders every portrait so a swap never waits on a download", () => {
    const { container } = render(<Hero />);
    const sources = Array.from(container.querySelectorAll("img"))
      .map((img) => img.getAttribute("src")?.match(/avatar-[a-z-]+\.png/)?.[0])
      .filter(Boolean);

    for (const face of [
      "avatar-smile-clear.png",
      "avatar-frown-clear.png",
      "avatar-smile-gold.png",
      "avatar-smile-metal.png",
    ]) {
      expect(sources).toContain(face);
    }
  });

  it("pokes to the frown on hover and recovers on leave", async () => {
    const user = userEvent.setup();
    const { container } = render(<Hero />);
    const button = screen.getByRole("button", { name: /Portrait of/ });

    expect(visibleFace(container)).toBe("avatar-smile-clear.png");

    await user.hover(button);
    expect(visibleFace(container)).toBe("avatar-frown-clear.png");
    expect(screen.getByText("Debugging prod")).toBeInTheDocument();

    await user.unhover(button);
    expect(visibleFace(container)).toBe("avatar-smile-clear.png");
  });

  it("cycles the glasses on click and never lands on the frown", async () => {
    const user = userEvent.setup();
    const { container } = render(<Hero />);
    const button = screen.getByRole("button", { name: /Portrait of/ });

    const seen: (string | undefined)[] = [];
    for (let click = 0; click < 4; click += 1) {
      await user.click(button);
      seen.push(visibleFace(container));
    }

    expect(seen).not.toContain("avatar-frown-clear.png");
    // Three pairs of glasses, so a run of four clicks has to repeat one.
    expect(new Set(seen).size).toBeGreaterThan(1);
  });

  it("announces a click, but leaves the idle loop silent", async () => {
    const user = userEvent.setup();
    const { container } = render(<Hero />);
    const live = container.querySelector('[aria-live="polite"]');

    expect(live).toHaveTextContent("");

    await user.click(screen.getByRole("button", { name: /Portrait of/ }));
    expect(live?.textContent).toMatch(/glasses/);
  });

  it("holds still when the visitor prefers reduced motion", async () => {
    mockReducedMotion(true);
    vi.useFakeTimers();

    try {
      const { container } = render(<Hero />);
      const before = visibleFace(container);

      // Well past every step in the loop.
      await vi.advanceTimersByTimeAsync(20_000);

      expect(visibleFace(container)).toBe(before);
    } finally {
      vi.useRealTimers();
    }
  });

  it("tilts the portrait with the pointer, and resets on leave", () => {
    const { container } = render(<Hero />);
    const section = container.querySelector("section#top") as HTMLElement;
    const wrap = section.querySelector("div.z-2") as HTMLElement;

    // jsdom reports a zero-sized box, so feed the handler a real one.
    vi.spyOn(section, "getBoundingClientRect").mockReturnValue({
      top: 0,
      left: 0,
      width: 1000,
      height: 800,
    } as DOMRect);

    section.dispatchEvent(
      new MouseEvent("mousemove", { clientX: 1000, clientY: 0, bubbles: true }),
    );
    expect(wrap.style.transform).toContain("rotateY(9.00deg)");

    section.dispatchEvent(new MouseEvent("mouseout", { bubbles: true }));
    expect(wrap.style.transform).toBe("");
  });
});
