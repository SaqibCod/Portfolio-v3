import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { siteConfig } from "@/lib/site-config";

import Home from "./page";

describe("Home", () => {
  it("renders a single level-one heading", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", {
        level: 1,
        name: `Hi, I’m ${siteConfig.name}`,
      }),
    ).toBeInTheDocument();
  });
});
