import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import TechPage from "./page";

describe("Tech behind this site", () => {
  it("renders a single level-one heading", () => {
    render(<TechPage />);

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Under the hood");
  });

  it("describes every feature in the HTML rather than behind a tab", () => {
    render(<TechPage />);

    // The export hides all but one feature behind an interactive tab row;
    // here each one is a card, so the endpoints are all in the page source.
    for (const endpoint of [
      "POST /api/v1/chat → text/event-stream",
      "GET /api/v1/github/summary · /activity",
      "GET /api/v1/status · /status/{slug}/history",
      "GET · POST /api/v1/guestbook",
      "POST /api/v1/contact",
    ]) {
      expect(screen.getByText(endpoint)).toBeInTheDocument();
    }
  });

  it("labels the architecture diagram for screen readers", () => {
    render(<TechPage />);

    expect(
      screen.getByRole("img", { name: /Four stages/ }),
    ).toBeInTheDocument();
  });
});
