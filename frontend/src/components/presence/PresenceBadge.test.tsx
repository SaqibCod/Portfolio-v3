import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PresenceBadge } from "@/components/presence/PresenceBadge";
import {
  PresenceContext,
  presenceFallback,
  type PresenceContextValue,
} from "@/components/presence/usePresence";

/** Renders the badge against a fixed presence state. */
function renderWith(state: Partial<PresenceContextValue>) {
  return render(
    <PresenceContext.Provider value={{ ...presenceFallback, ...state }}>
      <PresenceBadge />
    </PresenceContext.Provider>,
  );
}

describe("PresenceBadge", () => {
  it("shows the count once two people are on the page", () => {
    renderWith({ connected: true, pageCount: 2, siteCount: 5 });

    expect(screen.getByText("2 here now")).toBeInTheDocument();
  });

  it("hides itself while disconnected, however recent the count", () => {
    // A number from before the socket dropped is not a number any more, and
    // showing it would state something false.
    renderWith({ connected: false, pageCount: 7, siteCount: 9 });

    expect(screen.queryByText(/here now/)).not.toBeInTheDocument();
  });

  it("hides itself for a single viewer", () => {
    // True, but it tells the visitor only that they are where they are.
    renderWith({ connected: true, pageCount: 1, siteCount: 1 });

    expect(screen.queryByText(/here now/)).not.toBeInTheDocument();
  });

  it("hides itself when the count has not arrived yet", () => {
    renderWith({ connected: true, pageCount: 0, siteCount: 0 });

    expect(screen.queryByText(/here now/)).not.toBeInTheDocument();
  });

  it("renders nothing at all with no provider above it", () => {
    // Presence is a decoration: a page that has not opted into a socket, and a
    // test that renders the header on its own, must both still work.
    const { container } = render(<PresenceBadge />);

    expect(container).toBeEmptyDOMElement();
  });

  it("counts the page, not the site", () => {
    // The chip sits in a page's header; the site total is for other surfaces.
    renderWith({ connected: true, pageCount: 3, siteCount: 11 });

    expect(screen.getByText("3 here now")).toBeInTheDocument();
    expect(screen.queryByText("11 here now")).not.toBeInTheDocument();
  });

  it("uses the footer's longer wording on request", () => {
    render(
      <PresenceContext.Provider
        value={{ ...presenceFallback, connected: true, pageCount: 4 }}
      >
        <PresenceBadge wording="long" live={false} />
      </PresenceContext.Provider>,
    );

    expect(screen.getByText("4 people here now")).toBeInTheDocument();
  });

  it("pulses its dot in the header and not in the footer", () => {
    const state = { ...presenceFallback, connected: true, pageCount: 2 };

    const { container: header } = render(
      <PresenceContext.Provider value={state}>
        <PresenceBadge />
      </PresenceContext.Provider>,
    );
    expect(header.querySelector("i")?.className).toContain("dot-pulse");

    const { container: footer } = render(
      <PresenceContext.Provider value={state}>
        <PresenceBadge live={false} />
      </PresenceContext.Provider>,
    );
    expect(footer.querySelector("i")?.className).not.toContain("dot-pulse");
  });

  it("is left out of the announcement queue", () => {
    // A count that re-announces on every join and leave is hostile to screen
    // reader users, and it repeats nothing that is not decoration.
    const { container } = render(
      <PresenceContext.Provider
        value={{ ...presenceFallback, connected: true, pageCount: 2 }}
      >
        <PresenceBadge />
      </PresenceContext.Provider>,
    );

    expect(container.querySelector("[aria-live]")).toBeNull();
  });
});
