import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AdminGate } from "@/components/admin/AdminGate";

import type {
  AdminGuestbookEntry,
  ModerationPages,
} from "@/lib/admin-guestbook";

const openModerationQueue = vi.hoisted(() => vi.fn());
const listForModeration = vi.hoisted(() => vi.fn());
const moderateEntry = vi.hoisted(() => vi.fn());
const deleteEntry = vi.hoisted(() => vi.fn());

vi.mock("@/lib/admin-guestbook", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/admin-guestbook")>()),
  openModerationQueue,
  listForModeration,
  moderateEntry,
  deleteEntry,
}));

const TOKEN = "a-very-long-admin-token-0123456789";

/** All three statuses, which is what one unlock loads. */
function pagesWith(pending: AdminGuestbookEntry[] = []): ModerationPages {
  const empty = { items: [], nextCursor: null };
  return { pending: { items: pending, nextCursor: null }, approved: empty, rejected: empty };
}

beforeEach(() => {
  openModerationQueue.mockResolvedValue({ ok: true, data: pagesWith() });
  listForModeration.mockResolvedValue({
    ok: true,
    data: { items: [], nextCursor: null },
  });
});

afterEach(() => {
  vi.clearAllMocks();
});

async function enterToken(
  user: ReturnType<typeof userEvent.setup>,
  value = TOKEN,
) {
  await user.type(screen.getByLabelText("Admin token"), value);
  await user.click(screen.getByRole("button", { name: "Unlock" }));
}

describe("AdminGate", () => {
  it("asks for a token before showing anything else", () => {
    render(<AdminGate />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Admin" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Admin token")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Moderation" }),
    ).not.toBeInTheDocument();
  });

  it("masks the token and never autocompletes it", () => {
    render(<AdminGate />);

    const input = screen.getByLabelText("Admin token");
    expect(input).toHaveAttribute("type", "password");
    expect(input).toHaveAttribute("autocomplete", "off");
    expect(input).toHaveAttribute("spellcheck", "false");
  });

  it("can reveal the token so a paste is checkable", async () => {
    const user = userEvent.setup();
    render(<AdminGate />);

    await user.click(screen.getByRole("button", { name: "Show token" }));

    expect(screen.getByLabelText("Admin token")).toHaveAttribute(
      "type",
      "text",
    );
    expect(
      screen.getByRole("button", { name: "Hide token" }),
    ).toBeInTheDocument();
  });

  it("refuses to send an empty token", async () => {
    const user = userEvent.setup();
    render(<AdminGate />);

    await user.click(screen.getByRole("button", { name: "Unlock" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Paste your token first.",
    );
    expect(openModerationQueue).not.toHaveBeenCalled();
  });

  it("opens the queue once the server accepts the token", async () => {
    const user = userEvent.setup();
    render(<AdminGate />);

    await enterToken(user);

    expect(
      await screen.findByRole("heading", { level: 1, name: "Moderation" }),
    ).toBeInTheDocument();
    expect(openModerationQueue).toHaveBeenCalledWith(TOKEN);
    expect(screen.queryByLabelText("Admin token")).not.toBeInTheDocument();
  });

  it("reports a rejected token without opening anything", async () => {
    openModerationQueue.mockResolvedValue({
      ok: false,
      kind: "unauthorized",
      message: "Token rejected (401). Check it and try again.",
    });

    const user = userEvent.setup();
    render(<AdminGate />);

    await enterToken(user, "not-the-right-token");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Token rejected (401)",
    );
    expect(
      screen.queryByRole("heading", { name: "Moderation" }),
    ).not.toBeInTheDocument();
  });

  it("marks the field invalid when the token is refused", async () => {
    openModerationQueue.mockResolvedValue({
      ok: false,
      kind: "unauthorized",
      message: "Token rejected (401). Check it and try again.",
    });

    const user = userEvent.setup();
    render(<AdminGate />);

    await enterToken(user, "wrong");

    const input = await screen.findByLabelText("Admin token");
    await waitFor(() => expect(input).toHaveAttribute("aria-invalid", "true"));
    expect(input).toHaveAccessibleDescription(/Token rejected/);
  });

  it("keeps the token out of every kind of browser storage", async () => {
    const user = userEvent.setup();
    render(<AdminGate />);

    await enterToken(user);
    await screen.findByRole("heading", { name: "Moderation" });

    // The acceptance criterion: in memory only, so closing the tab signs out.
    expect(window.localStorage.getItem("token")).toBeNull();
    expect(JSON.stringify({ ...window.localStorage })).not.toContain(TOKEN);
    expect(JSON.stringify({ ...window.sessionStorage })).not.toContain(TOKEN);
    expect(document.cookie).not.toContain(TOKEN);
    expect(window.location.search).not.toContain(TOKEN);
    expect(document.body.innerHTML).not.toContain(TOKEN);
  });

  it("clears the token and closes the queue on Lock", async () => {
    const user = userEvent.setup();
    render(<AdminGate />);

    await enterToken(user);
    await user.click(await screen.findByRole("button", { name: /lock/i }));

    expect(await screen.findByLabelText("Admin token")).toHaveValue("");
    expect(
      screen.queryByRole("heading", { name: "Moderation" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Locked");
  });

  it("closes the gate again when the server later rejects the token", async () => {
    moderateEntry.mockResolvedValue({
      ok: false,
      kind: "unauthorized",
      message: "Token rejected (401). Check it and try again.",
    });
    openModerationQueue.mockResolvedValue({
      ok: true,
      data: pagesWith([
        {
          id: "11111111-2222-3333-4444-555555555555",
          name: "Priya",
          message: "A pending note",
          createdAt: "2026-11-20T10:15:00Z",
          status: "pending",
          moderatedAt: null,
        },
      ]),
    });

    const user = userEvent.setup();
    render(<AdminGate />);

    await enterToken(user);
    await user.click(await screen.findByRole("button", { name: "Approve" }));

    // A rotated or revoked token must not go on being held.
    expect(await screen.findByLabelText("Admin token")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      /server rejected that token/,
    );
  });

  it("says where the token does and does not go", () => {
    render(<AdminGate />);

    expect(screen.getByText(/Kept in memory only/)).toBeInTheDocument();
    expect(
      screen.getByText(/only as an Authorization header/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Never stored in the site’s code, cookies or local storage/),
    ).toBeInTheDocument();
  });
});
