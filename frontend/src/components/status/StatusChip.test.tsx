import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StatusChip } from "@/components/status/StatusChip";

describe("StatusChip", () => {
  it("renders nothing until a status exists", () => {
    const { container } = render(<StatusChip />);

    expect(container).toBeEmptyDOMElement();
  });

  it("links through to the status page", () => {
    render(<StatusChip status="operational" />);

    expect(
      screen.getByRole("link", { name: "All systems operational" }),
    ).toHaveAttribute("href", "/status");
  });

  it("labels every state the contract allows", () => {
    const cases = [
      ["operational", "All systems operational"],
      ["degraded", "Degraded performance"],
      ["down", "Service disruption"],
      ["unknown", "Status unknown"],
    ] as const;

    for (const [status, label] of cases) {
      const { unmount } = render(<StatusChip status={status} />);
      expect(screen.getByRole("link", { name: label })).toBeInTheDocument();
      unmount();
    }
  });

  it("pulses only while healthy", () => {
    const { container: ok } = render(<StatusChip status="operational" />);
    expect(ok.querySelector("i")?.className).toContain("dot-pulse");

    const { container: bad } = render(<StatusChip status="down" />);
    expect(bad.querySelector("i")?.className).not.toContain("dot-pulse");
  });

  it("colours the dot by state", () => {
    const { container } = render(<StatusChip status="degraded" />);

    expect(container.querySelector("i")?.className).toContain("bg-warn");
  });
});
