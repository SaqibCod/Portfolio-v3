import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { GuestbookList } from "@/components/guestbook/GuestbookList";

import type { GuestbookEntry, GuestbookPage } from "@/lib/guestbook";

const fetchMoreApprovedEntries = vi.hoisted(() => vi.fn());

vi.mock("@/lib/guestbook", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/guestbook")>()),
  fetchMoreApprovedEntries,
}));

function entry(id: string, message: string): GuestbookEntry {
  return {
    id,
    name: `Visitor ${id}`,
    message,
    createdAt: "2026-11-20T10:15:00Z",
  };
}

function page(items: GuestbookEntry[], nextCursor: string | null): GuestbookPage {
  return { items, nextCursor };
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("GuestbookList", () => {
  it("renders the server-fetched first page", () => {
    render(
      <GuestbookList
        initialPage={page([entry("1", "First note"), entry("2", "Second note")], null)}
      />,
    );

    expect(screen.getByText("First note")).toBeInTheDocument();
    expect(screen.getByText("Second note")).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(2);
  });

  it("gives each note a machine-readable date", () => {
    render(<GuestbookList initialPage={page([entry("1", "A note")], null)} />);

    const time = screen.getByText("Nov 20, 2026");
    expect(time.tagName).toBe("TIME");
    expect(time).toHaveAttribute("datetime", "2026-11-20T10:15:00Z");
  });

  it("offers no way to load more when the list is complete", () => {
    render(<GuestbookList initialPage={page([entry("1", "A note")], null)} />);

    expect(
      screen.queryByRole("button", { name: /load more/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText("You’ve reached the very first note."),
    ).toBeInTheDocument();
  });

  it("offers to load more while a cursor remains", () => {
    render(<GuestbookList initialPage={page([entry("1", "A note")], "cursor-1")} />);

    expect(
      screen.getByRole("button", { name: /load more notes/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("Showing 1 notes")).toBeInTheDocument();
  });

  it("appends the next page without dropping what is already shown", async () => {
    fetchMoreApprovedEntries.mockResolvedValue({
      ok: true,
      page: page([entry("2", "Second note")], null),
    });

    const user = userEvent.setup();
    render(<GuestbookList initialPage={page([entry("1", "First note")], "cursor-1")} />);

    await user.click(screen.getByRole("button", { name: /load more notes/i }));

    expect(await screen.findByText("Second note")).toBeInTheDocument();
    expect(screen.getByText("First note")).toBeInTheDocument();
    expect(fetchMoreApprovedEntries).toHaveBeenCalledWith("cursor-1");
  });

  it("stops offering more once the server runs out of cursors", async () => {
    fetchMoreApprovedEntries.mockResolvedValue({
      ok: true,
      page: page([entry("2", "Second note")], null),
    });

    const user = userEvent.setup();
    render(<GuestbookList initialPage={page([entry("1", "First note")], "cursor-1")} />);

    await user.click(screen.getByRole("button", { name: /load more notes/i }));

    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: /load more/i }),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.getByText("You’ve reached the very first note."),
    ).toBeInTheDocument();
  });

  it("follows each cursor the server hands back", async () => {
    fetchMoreApprovedEntries
      .mockResolvedValueOnce({
        ok: true,
        page: page([entry("2", "Second note")], "cursor-2"),
      })
      .mockResolvedValueOnce({
        ok: true,
        page: page([entry("3", "Third note")], null),
      });

    const user = userEvent.setup();
    render(<GuestbookList initialPage={page([entry("1", "First note")], "cursor-1")} />);

    await user.click(screen.getByRole("button", { name: /load more notes/i }));
    await screen.findByText("Second note");
    await user.click(screen.getByRole("button", { name: /load more notes/i }));

    expect(await screen.findByText("Third note")).toBeInTheDocument();
    expect(fetchMoreApprovedEntries).toHaveBeenNthCalledWith(1, "cursor-1");
    expect(fetchMoreApprovedEntries).toHaveBeenNthCalledWith(2, "cursor-2");
    expect(screen.getAllByRole("article")).toHaveLength(3);
  });

  it("never shows the same note twice if a cursor is somehow replayed", async () => {
    fetchMoreApprovedEntries.mockResolvedValue({
      ok: true,
      // The first page's entry comes back again, which a page boundary moving
      // under the list could cause.
      page: page([entry("1", "First note"), entry("2", "Second note")], null),
    });

    const user = userEvent.setup();
    render(<GuestbookList initialPage={page([entry("1", "First note")], "cursor-1")} />);

    await user.click(screen.getByRole("button", { name: /load more notes/i }));

    await waitFor(() => expect(screen.getAllByRole("article")).toHaveLength(2));
    expect(screen.getAllByText("First note")).toHaveLength(1);
  });

  it("reports a failed load without losing the list", async () => {
    fetchMoreApprovedEntries.mockResolvedValue({
      ok: false,
      message: "Couldn’t load more notes. Check your connection and retry.",
    });

    const user = userEvent.setup();
    render(<GuestbookList initialPage={page([entry("1", "First note")], "cursor-1")} />);

    await user.click(screen.getByRole("button", { name: /load more notes/i }));

    expect(await screen.findByText(/Couldn’t load more notes/)).toBeInTheDocument();
    expect(screen.getByText("First note")).toBeInTheDocument();
    // Still retryable: the cursor was not spent by a failure.
    expect(
      screen.getByRole("button", { name: /load more notes/i }),
    ).toBeInTheDocument();
  });

  it("shows the visitor's own note as awaiting approval", () => {
    render(
      <GuestbookList
        initialPage={page([entry("1", "An approved note")], null)}
        pendingEntry={{
          id: "mine",
          name: "Priya",
          message: "My brand new note",
          createdAt: "2026-11-20T12:00:00Z",
        }}
      />,
    );

    expect(screen.getByText("My brand new note")).toBeInTheDocument();
    expect(
      screen.getByText(/Awaiting approval · only you see this/),
    ).toBeInTheDocument();
  });

  it("shows the empty state only when there is genuinely nothing", () => {
    render(<GuestbookList initialPage={page([], null)} />);

    expect(
      screen.getByRole("heading", { name: "Be the first" }),
    ).toBeInTheDocument();
  });

  it("shows the visitor's pending note instead of the empty state", () => {
    render(
      <GuestbookList
        initialPage={page([], null)}
        pendingEntry={{
          id: "mine",
          name: "Priya",
          message: "The very first note",
          createdAt: "2026-11-20T12:00:00Z",
        }}
      />,
    );

    // "Be the first" beside their own just-written note would read as if it
    // had been lost.
    expect(
      screen.queryByRole("heading", { name: "Be the first" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("The very first note")).toBeInTheDocument();
  });

  it("says so when the first page could not be fetched", () => {
    render(<GuestbookList initialPage={page([], null)} unavailable />);

    expect(screen.getByRole("status")).toHaveTextContent(
      /notes couldn’t be loaded/i,
    );
    // Not the empty state: "be the first" would be a claim about the data,
    // which is exactly what is unknown here.
    expect(
      screen.queryByRole("heading", { name: "Be the first" }),
    ).not.toBeInTheDocument();
  });

  it("names the list for assistive technology", () => {
    render(<GuestbookList initialPage={page([entry("1", "A note")], null)} />);

    expect(
      screen.getByRole("region", { name: "Latest notes" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "Latest notes" }),
    ).toBeInTheDocument();
  });

  it("counts what it has rather than claiming a total it cannot see", () => {
    // The contract's page carries no total, so an exact count is only knowable
    // once the end of the list has been reached.
    const { unmount } = render(
      <GuestbookList initialPage={page([entry("1", "A note")], "cursor-1")} />,
    );
    expect(screen.getByText("1+ notes")).toBeInTheDocument();
    unmount();

    render(<GuestbookList initialPage={page([entry("1", "A note")], null)} />);
    expect(screen.getByText("1 note")).toBeInTheDocument();
  });
});
