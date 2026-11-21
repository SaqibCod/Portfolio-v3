import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { TopLanguages } from "@/components/github/TopLanguages";

describe("TopLanguages", () => {
  it("names each language and its share", () => {
    render(
      <TopLanguages
        languages={[
          { name: "Java", percentage: 48, color: "#b07219" },
          { name: "TypeScript", percentage: 31, color: "#3178c6" },
        ]}
      />,
    );

    expect(screen.getByText("Java 48%")).toBeInTheDocument();
    expect(screen.getByText("TypeScript 31%")).toBeInTheDocument();
  });

  it("drops a trailing .0 but keeps a meaningful decimal", () => {
    render(
      <TopLanguages
        languages={[
          { name: "Java", percentage: 48.0, color: "#b07219" },
          { name: "Python", percentage: 9.4, color: "#3572A5" },
        ]}
      />,
    );

    expect(screen.getByText("Java 48%")).toBeInTheDocument();
    expect(screen.getByText("Python 9.4%")).toBeInTheDocument();
  });

  it("sizes each bar segment by its share", () => {
    const { container } = render(
      <TopLanguages
        languages={[
          { name: "Java", percentage: 48, color: "#b07219" },
          { name: "TypeScript", percentage: 31, color: "#3178c6" },
        ]}
      />,
    );

    // GitHub's own hex colours, which jsdom reports back as rgb().
    const segments = container.querySelectorAll("div[aria-hidden] > i");
    expect(segments[0]).toHaveStyle({
      width: "48%",
      backgroundColor: "rgb(176, 114, 25)",
    });
    expect(segments[1]).toHaveStyle({
      width: "31%",
      backgroundColor: "rgb(49, 120, 198)",
    });
  });

  it("falls back to the muted token when GitHub publishes no colour", () => {
    render(<TopLanguages languages={[{ name: "Brainfuck", percentage: 2 }]} />);

    expect(screen.getByText("Brainfuck 2%")).toBeInTheDocument();
  });

  it("keeps the bar out of the accessibility tree", () => {
    // It is a wordless second copy of the legend below it.
    const { container } = render(
      <TopLanguages languages={[{ name: "Java", percentage: 48, color: "#b07219" }]} />,
    );

    expect(container.querySelector("div")).toHaveAttribute("aria-hidden", "true");
  });

  it("renders nothing when there are no languages", () => {
    const { container } = render(<TopLanguages languages={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});
