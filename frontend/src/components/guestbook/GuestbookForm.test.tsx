import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { GuestbookForm } from "@/components/guestbook/GuestbookForm";
import { ThemeProvider } from "@/components/theme/ThemeProvider";

import type { GuestbookSubmitResult } from "@/lib/guestbook";

/**
 * The real widget injects Cloudflare's script, which jsdom cannot run, so it
 * is replaced by a stub that behaves like a solved challenge: it hands a token
 * to `onSuccess` and exposes `reset` on the ref the form resets through.
 *
 * `solveTurnstile` is left as an escape hatch for the "no token yet" case.
 */
let solveTurnstile = true;
const resetWidget = vi.fn();

vi.mock("@marsidev/react-turnstile", () => ({
  Turnstile: ({
    ref,
    onSuccess,
  }: {
    ref?: { current: { reset: () => void } | null };
    onSuccess?: (token: string) => void;
  }) => {
    if (ref) ref.current = { reset: resetWidget };
    if (solveTurnstile) onSuccess?.("test-widget-token");
    return <div data-testid="turnstile" />;
  },
}));

const submitGuestbookEntry = vi.hoisted(() => vi.fn());

vi.mock("@/lib/guestbook", async (importOriginal) => ({
  // The schema stays real: client-side validation is part of what is tested.
  ...(await importOriginal<typeof import("@/lib/guestbook")>()),
  submitGuestbookEntry,
}));

function accepted(): GuestbookSubmitResult {
  return { ok: true, id: "11111111-2222-3333-4444-555555555555" };
}

function renderForm(onSigned?: (entry: unknown) => void) {
  return render(
    <ThemeProvider>
      <GuestbookForm onSigned={onSigned} />
    </ThemeProvider>,
  );
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Name"), "Priya");
  await user.type(
    screen.getByLabelText("Message"),
    "Loved the status page idea!",
  );
}

function sign(user: ReturnType<typeof userEvent.setup>) {
  return user.click(screen.getByRole("button", { name: /sign the guestbook/i }));
}

beforeEach(() => {
  solveTurnstile = true;
  submitGuestbookEntry.mockResolvedValue(accepted());
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("GuestbookForm", () => {
  it("renders a labelled field for everything the contract accepts", () => {
    renderForm();

    expect(screen.getByLabelText("Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toBeInTheDocument();
    expect(
      screen.getByRole("form", { name: "Guestbook form" }),
    ).toBeInTheDocument();
  });

  it("asks for no email or anything else identifying", () => {
    renderForm();

    // The guestbook is anonymous by design, so there is no such field to fill.
    expect(screen.queryByLabelText("Email")).not.toBeInTheDocument();
    expect(screen.getAllByRole("textbox")).toHaveLength(2);
  });

  it("caps each field at the contract's length", () => {
    renderForm();

    expect(screen.getByLabelText("Name")).toHaveAttribute("maxlength", "50");
    expect(screen.getByLabelText("Message")).toHaveAttribute(
      "maxlength",
      "500",
    );
  });

  it("counts characters against each limit as they are typed", async () => {
    const user = userEvent.setup();
    renderForm();

    expect(screen.getByText("0/50")).toBeInTheDocument();
    expect(screen.getByText("0/500")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Name"), "Priya");

    expect(await screen.findByText("5/50")).toBeInTheDocument();
  });

  it("ties each counter to its own field for a screen reader", () => {
    renderForm();

    expect(screen.getByLabelText("Name")).toHaveAccessibleDescription("0/50");
    expect(screen.getByLabelText("Message")).toHaveAccessibleDescription(
      "0/500",
    );
  });

  it("shows a message per invalid field instead of submitting", async () => {
    const user = userEvent.setup();
    renderForm();

    await sign(user);

    expect(
      await screen.findByText("Tell me how to credit you."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Message must be at least 2 characters."),
    ).toBeInTheDocument();
    expect(submitGuestbookEntry).not.toHaveBeenCalled();
  });

  it("rejects a one-character message, as the contract does", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("Name"), "Priya");
    await user.type(screen.getByLabelText("Message"), "k");
    await sign(user);

    expect(
      await screen.findByText("Message must be at least 2 characters."),
    ).toBeInTheDocument();
    expect(submitGuestbookEntry).not.toHaveBeenCalled();
  });

  it("marks an invalid field and ties the message to it", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("Message"), "k");
    await sign(user);

    const message = screen.getByLabelText("Message");
    await waitFor(() =>
      expect(message).toHaveAttribute("aria-invalid", "true"),
    );
    expect(message).toHaveAccessibleDescription(
      /Message must be at least 2 characters/,
    );
  });

  it("sends the trimmed values and the widget's token", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    await waitFor(() => expect(submitGuestbookEntry).toHaveBeenCalledTimes(1));
    expect(submitGuestbookEntry).toHaveBeenCalledWith(
      {
        name: "Priya",
        message: "Loved the status page idea!",
        website: "",
      },
      "test-widget-token",
    );
  });

  it("replaces the form with a pending confirmation once it has sent", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    expect(
      await screen.findByRole("heading", { name: "Thanks for signing!" }),
    ).toBeInTheDocument();
    // The note is not public yet, and the panel has to say so rather than
    // implying the visitor will find it in the list.
    expect(screen.getByRole("status")).toHaveTextContent("Pending approval");
    expect(screen.getByRole("status")).toHaveTextContent(
      /waiting for me to approve it/,
    );
    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
  });

  it("hands the accepted note up so the list can show it as pending", async () => {
    const onSigned = vi.fn();
    const user = userEvent.setup();
    renderForm(onSigned);

    await fillValidForm(user);
    await sign(user);

    await waitFor(() => expect(onSigned).toHaveBeenCalledTimes(1));
    expect(onSigned).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "11111111-2222-3333-4444-555555555555",
        name: "Priya",
        message: "Loved the status page idea!",
      }),
    );
  });

  it("comes back to an empty form from the confirmation", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);
    await user.click(
      await screen.findByRole("button", { name: /write another/i }),
    );

    expect(screen.getByLabelText("Name")).toHaveValue("");
    expect(screen.getByLabelText("Message")).toHaveValue("");
    expect(screen.getByText("0/500")).toBeInTheDocument();
  });

  it("asks the widget for a fresh token after every attempt", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    // Turnstile tokens are single-use, so a spent one must never be reused.
    await waitFor(() => expect(resetWidget).toHaveBeenCalled());
  });

  it("puts server field errors back on the fields they name", async () => {
    submitGuestbookEntry.mockResolvedValue({
      ok: false,
      kind: "fields",
      message: "One or more fields are invalid.",
      fieldErrors: { message: "must be between 2 and 500 characters" },
    } satisfies GuestbookSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    expect(
      await screen.findByText("must be between 2 and 500 characters"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it("explains a spent daily limit with the wait the server gave", async () => {
    submitGuestbookEntry.mockResolvedValue({
      ok: false,
      kind: "rateLimit",
      message: "That is the limit for now — try again in about 14 hours.",
    } satisfies GuestbookSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("You’ve signed twice today");
    expect(alert).toHaveTextContent("about 14 hours");
  });

  it("explains a failed bot check and keeps the typed note", async () => {
    submitGuestbookEntry.mockResolvedValue({
      ok: false,
      kind: "turnstile",
      message: "Please complete the check again, then resend.",
    } satisfies GuestbookSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Couldn’t verify you’re human");
    // Nothing the visitor wrote is thrown away by a failure they can retry.
    expect(screen.getByLabelText("Message")).toHaveValue(
      "Loved the status page idea!",
    );
  });

  it("explains an unreachable server", async () => {
    submitGuestbookEntry.mockResolvedValue({
      ok: false,
      kind: "unknown",
      message: "Couldn’t reach the server. Check your connection and resend.",
    } satisfies GuestbookSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    expect(await screen.findByRole("alert")).toHaveTextContent("Note not sent");
  });

  it("moves focus to the banner so it is not missed", async () => {
    submitGuestbookEntry.mockResolvedValue({
      ok: false,
      kind: "unknown",
      message: "Something went wrong.",
    } satisfies GuestbookSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    await waitFor(() => expect(screen.getByRole("alert")).toHaveFocus());
  });

  it("says so instead of submitting when the bot check has not finished", async () => {
    solveTurnstile = false;
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await sign(user);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Still checking you’re human",
    );
    expect(submitGuestbookEntry).not.toHaveBeenCalled();
  });

  it("keeps the submit button usable while the check is still running", () => {
    solveTurnstile = false;
    renderForm();

    // A dead button with no explanation is worse than one that explains itself
    // on press, so it is never disabled for a missing token.
    expect(
      screen.getByRole("button", { name: /sign the guestbook/i }),
    ).toBeEnabled();
  });

  it("carries a honeypot that is hidden from people and from screen readers", () => {
    renderForm();

    const honeypot = document.querySelector<HTMLInputElement>(
      'input[name="website"]',
    );

    expect(honeypot).not.toBeNull();
    expect(honeypot).toHaveAttribute("tabindex", "-1");
    expect(honeypot).toHaveAttribute("autocomplete", "off");
    expect(honeypot?.closest("[aria-hidden='true']")).not.toBeNull();
    // Out of the accessibility tree: a screen reader is offered the two real
    // fields and never this one.
    expect(screen.getAllByRole("textbox")).not.toContain(honeypot);
  });

  it("tells the visitor the note is held for approval before they send it", () => {
    renderForm();

    // Setting the expectation up front is what keeps the pending state from
    // reading as a failure afterwards.
    expect(
      screen.getByText(/Held for approval before it’s public/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/will be public once approved/),
    ).toBeInTheDocument();
  });
});
