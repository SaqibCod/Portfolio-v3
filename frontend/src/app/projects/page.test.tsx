import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { getAllProjects } from "@/lib/projects";

import ProjectsPage from "./page";

describe("Projects", () => {
  it("renders a single level-one heading", () => {
    render(<ProjectsPage />);

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Projects");
  });

  it("puts every project in the server-rendered HTML", () => {
    render(<ProjectsPage />);

    for (const project of getAllProjects()) {
      expect(
        screen.getByRole("heading", { level: 2, name: project.title }),
      ).toBeInTheDocument();
      expect(screen.getByText(project.summary)).toBeInTheDocument();
    }
  });

  it("links each project to its case study", () => {
    render(<ProjectsPage />);

    const hrefs = screen
      .getAllByRole("link", { name: "Case study" })
      .map((link) => link.getAttribute("href"));

    expect(hrefs).toEqual(getAllProjects().map((project) => project.href));
  });
});
