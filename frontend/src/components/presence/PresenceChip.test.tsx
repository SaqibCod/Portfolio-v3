import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PresenceChip } from "@/components/presence/PresenceChip";

describe("PresenceChip", () => {
  it("renders nothing until a count exists", () => {
    const { container } = render(<PresenceChip />);

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing for a nonsensical count", () => {
    const { container } = render(<PresenceChip count={-1} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("shows zero rather than hiding when nobody is here", () => {
    render(<PresenceChip count={0} />);

    expect(screen.getByText("0 here now")).toBeInTheDocument();
  });

  it("uses the header's short wording by default", () => {
    render(<PresenceChip count={11} />);

    expect(screen.getByText("11 here now")).toBeInTheDocument();
  });

  it("uses the footer's long wording on request", () => {
    render(<PresenceChip count={11} wording="long" />);

    expect(screen.getByText("11 people here now")).toBeInTheDocument();
  });

  it("says person, not people, for a single viewer", () => {
    render(<PresenceChip count={1} wording="long" />);

    expect(screen.getByText("1 person here now")).toBeInTheDocument();
  });

  it("groups large counts with a fixed locale, so SSR and the client agree", () => {
    render(<PresenceChip count={1284} />);

    expect(screen.getByText("1,284 here now")).toBeInTheDocument();
  });

  it("pulses in the header and sits still in the footer", () => {
    const { container: live } = render(<PresenceChip count={5} />);
    expect(live.querySelector("i")?.className).toContain("dot-pulse");

    const { container: still } = render(
      <PresenceChip count={5} live={false} />,
    );
    expect(still.querySelector("i")?.className).not.toContain("dot-pulse");
  });
});
