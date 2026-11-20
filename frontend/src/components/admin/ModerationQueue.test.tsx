import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ModerationQueue } from "@/components/admin/ModerationQueue";

import type {
  AdminGuestbookEntry,
  AdminGuestbookPage,
  ModerationPages,
  ModerationStatus,
} from "@/lib/admin-guestbook";

const listForModeration = vi.hoisted(() => vi.fn());
const moderateEntry = vi.hoisted(() => vi.fn());
const deleteEntry = vi.hoisted(() => vi.fn());

vi.mock("@/lib/admin-guestbook", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/admin-guestbook")>()),
  listForModeration,
  moderateEntry,
  deleteEntry,
}));

const TOKEN = "a-very-long-admin-token-0123456789";

function entry(
  id: string,
  message: string,
  status: ModerationStatus = "pending",
): AdminGuestbookEntry {
  return {
    id,
    name: `Visitor ${id}`,
    message,
    createdAt: "2026-11-20T10:15:00Z",
    status,
    moderatedAt: status === "pending" ? null : "2026-11-20T11:00:00Z",
  };
}

function page(
  items: AdminGuestbookEntry[],
  nextCursor: string | null = null,
): AdminGuestbookPage {
  return { items, nextCursor };
}

/**
 * All three statuses, as the unlock hands them over. A test gives only the
 * ones it cares about; the rest default to empty.
 */
function pages(overrides: Partial<ModerationPages> = {}): ModerationPages {
  return {
    pending: page([]),
    approved: page([]),
    rejected: page([]),
    ...overrides,
  };
}

const onUnauthorized = vi.fn();

function renderQueue(initialPages: ModerationPages) {
  return render(
    <ModerationQueue
      token={TOKEN}
      initialPages={initialPages}
      onUnauthorized={onUnauthorized}
    />,
  );
}

/** One pending note, which is what most of these tests act on. */
function onePending() {
  return pages({ pending: page([entry("1", "A pending note")]) });
}

beforeEach(() => {
  listForModeration.mockResolvedValue({ ok: true, data: page([]) });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("ModerationQueue", () => {
  it("shows the pending queue it was handed", () => {
    renderQueue(onePending());

    expect(screen.getByText("A pending note")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: "Moderation" }),
    ).toBeInTheDocument();
  });

  it("fetches nothing on mount — the unlock already loaded all three", () => {
    renderQueue(
      pages({
        pending: page([entry("1", "A pending note")]),
        approved: page([entry("2", "An approved note", "approved")]),
      }),
    );

    expect(listForModeration).not.toHaveBeenCalled();
  });

  it("counts all three statuses from the pages it was given", () => {
    renderQueue(
      pages({
        pending: page([entry("1", "One"), entry("2", "Two")]),
        approved: page([entry("3", "Three", "approved")]),
        rejected: page([
          entry("4", "Four", "rejected"),
          entry("5", "Five", "rejected"),
          entry("6", "Six", "rejected"),
        ]),
      }),
    );

    // Tab badges and stat tiles both read from the same map, so each count
    // appears twice.
    expect(screen.getAllByText("2")).toHaveLength(2);
    expect(screen.getAllByText("1")).toHaveLength(2);
    expect(screen.getAllByText("3")).toHaveLength(2);
  });

  it("shows the whole id so an entry can be matched to a log line", () => {
    renderQueue(
      pages({
        pending: page([entry("11111111-2222-3333-4444-555555555555", "A note")]),
      }),
    );

    expect(
      screen.getByText("id 11111111-2222-3333-4444-555555555555"),
    ).toBeInTheDocument();
  });

  it("filters to the status whose tab is selected", async () => {
    const user = userEvent.setup();
    renderQueue(
      pages({
        pending: page([entry("1", "A pending note")]),
        approved: page([entry("2", "An approved note", "approved")]),
      }),
    );

    expect(screen.queryByText("An approved note")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /approved/i }));

    expect(screen.getByText("An approved note")).toBeInTheDocument();
    expect(screen.queryByText("A pending note")).not.toBeInTheDocument();
  });

  it("moves between tabs with the arrow keys", async () => {
    const user = userEvent.setup();
    renderQueue(onePending());

    const pending = screen.getByRole("tab", { name: /pending/i });
    pending.focus();
    await user.keyboard("{ArrowRight}");

    const approved = screen.getByRole("tab", { name: /approved/i });
    expect(approved).toHaveAttribute("aria-selected", "true");
    expect(approved).toHaveFocus();
    // Only the selected tab is a tab stop; the arrow keys do the rest.
    expect(pending).toHaveAttribute("tabindex", "-1");
  });

  it("wraps around at the ends of the tab list", async () => {
    const user = userEvent.setup();
    renderQueue(pages());

    screen.getByRole("tab", { name: /pending/i }).focus();
    await user.keyboard("{ArrowLeft}");

    expect(screen.getByRole("tab", { name: /rejected/i })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("approves an entry and moves it to the approved tab", async () => {
    moderateEntry.mockResolvedValue({
      ok: true,
      data: entry("1", "A pending note", "approved"),
    });

    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Approve" }));

    expect(moderateEntry).toHaveBeenCalledWith(TOKEN, "1", "approved");
    await waitFor(() =>
      expect(screen.queryByText("A pending note")).not.toBeInTheDocument(),
    );
    await user.click(screen.getByRole("tab", { name: /approved/i }));
    expect(screen.getByText("A pending note")).toBeInTheDocument();
  });

  it("confirms an approval so the owner knows it is public now", async () => {
    moderateEntry.mockResolvedValue({
      ok: true,
      data: entry("1", "A pending note", "approved"),
    });

    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByText("Approved — now public")).toBeInTheDocument();
  });

  it("puts an entry back when the approval fails", async () => {
    moderateEntry.mockResolvedValue({
      ok: false,
      kind: "unknown",
      message: "That service is temporarily unavailable.",
    });

    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Approve" }));

    // Undo: the note is back in the pending queue, and the owner is told the
    // decision did not stick.
    expect(await screen.findByText(/Nothing was changed/)).toBeInTheDocument();
    expect(screen.getByText("A pending note")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
  });

  it("offers Reject on a pending entry and Unpublish on an approved one", async () => {
    const user = userEvent.setup();
    renderQueue(
      pages({
        pending: page([entry("1", "A pending note")]),
        approved: page([entry("2", "An approved note", "approved")]),
      }),
    );

    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /approved/i }));

    // Same endpoint, but "Reject" would misdescribe taking down something that
    // is already on the site.
    expect(
      screen.getByRole("button", { name: "Unpublish" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Approve" }),
    ).not.toBeInTheDocument();
  });

  it("says an approved entry was unpublished rather than rejected", async () => {
    moderateEntry.mockResolvedValue({
      ok: true,
      data: entry("2", "An approved note", "rejected"),
    });

    const user = userEvent.setup();
    renderQueue(
      pages({ approved: page([entry("2", "An approved note", "approved")]) }),
    );

    await user.click(screen.getByRole("tab", { name: /approved/i }));
    await user.click(screen.getByRole("button", { name: "Unpublish" }));

    expect(
      await screen.findByText("Unpublished — hidden from the site"),
    ).toBeInTheDocument();
  });

  it("never asks to delete without confirming first", async () => {
    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(deleteEntry).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Delete permanently?");
    expect(screen.getByRole("alert")).toHaveTextContent("This can’t be undone");
  });

  it("keeps the entry when the delete is cancelled", async () => {
    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(deleteEntry).not.toHaveBeenCalled();
    expect(screen.getByText("A pending note")).toBeInTheDocument();
  });

  it("deletes the entry once the owner confirms", async () => {
    deleteEntry.mockResolvedValue({ ok: true, data: null });

    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Delete" }));
    const confirm = screen.getByRole("alert");
    await user.click(within(confirm).getByRole("button", { name: "Delete" }));

    expect(deleteEntry).toHaveBeenCalledWith(TOKEN, "1");
    expect(await screen.findByText("Deleted permanently")).toBeInTheDocument();
    expect(screen.queryByText("A pending note")).not.toBeInTheDocument();
  });

  it("puts the entry back when the delete fails", async () => {
    deleteEntry.mockResolvedValue({
      ok: false,
      kind: "unknown",
      message: "That service is temporarily unavailable.",
    });

    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Delete" }));
    const confirm = screen.getByRole("alert");
    await user.click(within(confirm).getByRole("button", { name: "Delete" }));

    expect(await screen.findByText(/Nothing was deleted/)).toBeInTheDocument();
    expect(screen.getByText("A pending note")).toBeInTheDocument();
  });

  it("leaves an already-deleted entry gone", async () => {
    deleteEntry.mockResolvedValue({
      ok: false,
      kind: "notFound",
      message: "That entry is already gone.",
    });

    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Delete" }));
    const confirm = screen.getByRole("alert");
    await user.click(within(confirm).getByRole("button", { name: "Delete" }));

    // A 404 means it really is gone, so restoring it would be the wrong move.
    expect(
      await screen.findByText("That entry is already gone."),
    ).toBeInTheDocument();
    expect(screen.queryByText("A pending note")).not.toBeInTheDocument();
  });

  it("hands a rejected token back to the gate instead of retrying", async () => {
    moderateEntry.mockResolvedValue({
      ok: false,
      kind: "unauthorized",
      message: "Token rejected (401). Check it and try again.",
    });

    const user = userEvent.setup();
    renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Approve" }));

    await waitFor(() => expect(onUnauthorized).toHaveBeenCalledTimes(1));
  });

  it("shows inbox zero when the pending queue is clear", () => {
    renderQueue(pages());

    expect(
      screen.getByRole("heading", { name: "Inbox zero" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/New ones will email you/)).toBeInTheDocument();
  });

  it("names the other statuses when they are empty rather than saying inbox zero", async () => {
    const user = userEvent.setup();
    renderQueue(pages());

    await user.click(screen.getByRole("tab", { name: /rejected/i }));

    expect(
      screen.getByRole("heading", { name: "Nothing here" }),
    ).toBeInTheDocument();
    expect(screen.getByText("No rejected entries yet.")).toBeInTheDocument();
  });

  it("loads more of the tab the owner is looking at", async () => {
    listForModeration.mockResolvedValue({
      ok: true,
      data: page([entry("2", "Another pending note")]),
    });

    const user = userEvent.setup();
    renderQueue(
      pages({ pending: page([entry("1", "A pending note")], "cursor-1") }),
    );

    await user.click(screen.getByRole("button", { name: "Load more" }));

    expect(listForModeration).toHaveBeenCalledWith(TOKEN, "pending", "cursor-1");
    expect(await screen.findByText("Another pending note")).toBeInTheDocument();
    expect(screen.getByText("A pending note")).toBeInTheDocument();
  });

  it("offers no Load more on a status that is complete", () => {
    renderQueue(onePending());

    expect(
      screen.queryByRole("button", { name: "Load more" }),
    ).not.toBeInTheDocument();
  });

  it("marks a count as partial while more of that status may exist", () => {
    renderQueue(
      pages({ pending: page([entry("1", "A pending note")], "cursor-1") }),
    );

    // The contract's page carries no total, so a count with a cursor still
    // outstanding is a floor rather than a total.
    expect(screen.getByText("1+")).toBeInTheDocument();
  });

  it("announces every outcome, not just shows it", async () => {
    moderateEntry.mockResolvedValue({
      ok: true,
      data: entry("1", "A pending note", "approved"),
    });

    const user = userEvent.setup();
    const { container } = renderQueue(onePending());

    await user.click(screen.getByRole("button", { name: "Approve" }));

    const live = container.querySelector('[aria-live="polite"]');
    await waitFor(() => expect(live).toHaveTextContent("Approved — now public"));
  });

  it("ties each tab to the panel it controls", () => {
    renderQueue(onePending());

    const tab = screen.getByRole("tab", { name: /pending/i });
    expect(tab).toHaveAttribute("aria-controls", "panel-pending");
    expect(screen.getByRole("tabpanel")).toHaveAttribute(
      "aria-labelledby",
      "tab-pending",
    );
  });
});
