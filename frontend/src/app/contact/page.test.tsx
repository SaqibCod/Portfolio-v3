import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { siteConfig } from "@/lib/site-config";

import ContactPage, { metadata } from "./page";

// jsdom cannot run Cloudflare's script; this page's own markup is what matters
// here, so the widget is reduced to a marker.
vi.mock("@marsidev/react-turnstile", () => ({
  Turnstile: () => <div data-testid="turnstile" />,
}));

describe("Contact page", () => {
  it("renders a single level-one heading", () => {
    render(<ContactPage />);

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Let’s get in touch");
  });

  it("keeps a way to reach me in the HTML, independent of the API", () => {
    render(<ContactPage />);

    // The form needs the API; the address does not, so it is server-rendered
    // and still there if the backend is down.
    expect(
      screen.getByRole("link", { name: siteConfig.email }),
    ).toHaveAttribute("href", `mailto:${siteConfig.email}`);
  });

  it("server-renders the form fields rather than waiting for the widget", () => {
    render(<ContactPage />);

    expect(
      screen.getByRole("form", { name: "Contact form" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toBeInTheDocument();
  });

  it("exports a canonical URL and a description", () => {
    expect(metadata.title).toBe("Contact");
    expect(metadata.alternates?.canonical).toBe("/contact");
    expect(metadata.description).toContain("Send me a message");
  });

  it("emits breadcrumb structured data", () => {
    const { container } = render(<ContactPage />);

    const script = container.querySelector(
      'script[type="application/ld+json"]',
    );
    expect(script?.textContent).toContain("BreadcrumbList");
    expect(script?.textContent).toContain("/contact");
  });
});
