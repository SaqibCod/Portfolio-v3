import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Badge } from "@/components/ui/Badge";

function dotOf(container: HTMLElement): HTMLElement {
  const dot = container.querySelector("i");
  if (!dot) throw new Error("badge rendered no dot");
  return dot;
}

describe("Badge", () => {
  it("renders its label with no dot by default", () => {
    const { container } = render(<Badge>Next.js</Badge>);

    expect(screen.getByText("Next.js")).toBeInTheDocument();
    expect(container.querySelector("i")).toBeNull();
  });

  it("hides the dot from assistive tech", () => {
    const { container } = render(<Badge dot="ok">Operational</Badge>);

    expect(dotOf(container)).toHaveAttribute("aria-hidden", "true");
  });

  it("publishes the tone colour for the pulse ring", () => {
    const { container } = render(<Badge dot="warn">Degraded</Badge>);

    const classes = dotOf(container).className;
    expect(classes).toContain("bg-warn");
    expect(classes).toContain("[--dot-color:var(--warn)]");
  });

  it("does not animate unless it is live", () => {
    const { container } = render(<Badge dot="ok">Operational</Badge>);

    expect(dotOf(container).className).not.toContain("dot-pulse");
  });

  it("uses the design's expanding ring for live dots, not an opacity fade", () => {
    const { container } = render(
      <Badge dot="ok" live>
        11 here now
      </Badge>,
    );

    const classes = dotOf(container).className;
    expect(classes).toContain(
      "motion-safe:animate-[dot-pulse_2s_ease-in-out_infinite]",
    );
    // Tailwind's built-in pulse is an opacity fade — the wrong effect here.
    expect(classes).not.toContain("animate-pulse");
  });

  it("keeps the pulse behind motion-safe", () => {
    const { container } = render(
      <Badge dot="ok" live>
        Live
      </Badge>,
    );

    expect(dotOf(container).className).toContain("motion-safe:");
  });
});
