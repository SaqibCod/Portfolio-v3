import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import GuestbookPage, { metadata, revalidate } from "./page";

import type { GuestbookPage as GuestbookPageData } from "@/lib/guestbook";

// jsdom cannot run Cloudflare's script; this page's own markup is what matters
// here, so the widget is reduced to a marker.
vi.mock("@marsidev/react-turnstile", () => ({
  Turnstile: () => <div data-testid="turnstile" />,
}));

const fetchApprovedEntries = vi.hoisted(() => vi.fn());

vi.mock("@/lib/guestbook", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/guestbook")>()),
  fetchApprovedEntries,
}));

function page(items: GuestbookPageData["items"]): GuestbookPageData {
  return { items, nextCursor: null };
}

/** The page is an async server component, so it is awaited before rendering. */
async function renderPage() {
  return render(await GuestbookPage());
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("Guestbook page", () => {
  it("renders a single level-one heading", async () => {
    fetchApprovedEntries.mockResolvedValue(page([]));

    await renderPage();

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Guestbook");
  });

  it("server-renders the approved notes into the HTML", async () => {
    fetchApprovedEntries.mockResolvedValue(
      page([
        {
          id: "11111111-2222-3333-4444-555555555555",
          name: "Priya",
          message: "Loved the status page idea!",
          createdAt: "2026-11-20T10:15:00Z",
        },
      ]),
    );

    await renderPage();

    // The whole point of fetching on the server: the note is in the markup,
    // not something that appears after a round trip in the browser.
    expect(screen.getByText("Loved the status page idea!")).toBeInTheDocument();
    expect(screen.getByText("Priya")).toBeInTheDocument();
  });

  it("asks the API for the first page only", async () => {
    fetchApprovedEntries.mockResolvedValue(page([]));

    await renderPage();

    expect(fetchApprovedEntries).toHaveBeenCalledTimes(1);
    expect(fetchApprovedEntries).toHaveBeenCalledWith();
  });

  it("server-renders the form so it works without waiting for the widget", async () => {
    fetchApprovedEntries.mockResolvedValue(page([]));

    await renderPage();

    expect(
      screen.getByRole("form", { name: "Guestbook form" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toBeInTheDocument();
  });

  it("still renders the page when the API cannot be reached", async () => {
    fetchApprovedEntries.mockResolvedValue(null);

    await renderPage();

    // A backend outage must not take out a route whose job is to be indexable.
    expect(
      screen.getByRole("heading", { level: 1, name: "Guestbook" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("form", { name: "Guestbook form" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      /notes couldn’t be loaded/i,
    );
  });

  it("shows the empty state when nothing has been approved yet", async () => {
    fetchApprovedEntries.mockResolvedValue(page([]));

    await renderPage();

    expect(
      screen.getByRole("heading", { name: "Be the first" }),
    ).toBeInTheDocument();
  });

  it("exports a canonical URL and a description", () => {
    expect(metadata.title).toBe("Guestbook");
    expect(metadata.alternates?.canonical).toBe("/guestbook");
    expect(metadata.description).toContain("approved");
  });

  it("stays indexable", () => {
    // The opposite of /admin: this page is meant to be found.
    expect(metadata.robots).toBeUndefined();
  });

  it("revalidates rather than rendering on every request", () => {
    // A statically analyzable literal, which is what Next requires of this
    // export — an imported constant or an expression would not be allowed.
    expect(revalidate).toBe(60);
  });

  it("emits breadcrumb structured data", async () => {
    fetchApprovedEntries.mockResolvedValue(page([]));

    const { container } = await renderPage();

    const script = container.querySelector(
      'script[type="application/ld+json"]',
    );
    expect(script?.textContent).toContain("BreadcrumbList");
    expect(script?.textContent).toContain("/guestbook");
  });
});
