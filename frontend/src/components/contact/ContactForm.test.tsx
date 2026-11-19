import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ContactForm } from "@/components/contact/ContactForm";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import type { ContactSubmitResult } from "@/lib/contact";

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

const submitContactMessage = vi.hoisted(() => vi.fn());

vi.mock("@/lib/contact", async (importOriginal) => ({
  // The schema stays real: client-side validation is part of what is tested.
  ...(await importOriginal<typeof import("@/lib/contact")>()),
  submitContactMessage,
}));

function accepted(): ContactSubmitResult {
  return { ok: true, id: "11111111-2222-3333-4444-555555555555" };
}

function renderForm() {
  return render(
    <ThemeProvider>
      <ContactForm />
    </ThemeProvider>,
  );
}

/** Fills every required field with something the schema accepts. */
async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Full name"), "Ada Lovelace");
  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(
    screen.getByLabelText("Message"),
    "I would like to talk to you about a backend role.",
  );
}

function send(user: ReturnType<typeof userEvent.setup>) {
  return user.click(screen.getByRole("button", { name: /send message/i }));
}

beforeEach(() => {
  solveTurnstile = true;
  submitContactMessage.mockResolvedValue(accepted());
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("ContactForm", () => {
  it("renders a labelled field for everything the contract accepts", () => {
    renderForm();

    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Subject (optional)")).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toBeInTheDocument();
  });

  it("gives the fields the right types and autocomplete hints", () => {
    renderForm();

    expect(screen.getByLabelText("Email")).toHaveAttribute("type", "email");
    expect(screen.getByLabelText("Email")).toHaveAttribute(
      "autocomplete",
      "email",
    );
    expect(screen.getByLabelText("Full name")).toHaveAttribute(
      "autocomplete",
      "name",
    );
  });

  it("shows a message per invalid field instead of submitting", async () => {
    const user = userEvent.setup();
    renderForm();

    await send(user);

    expect(await screen.findByText("Tell me who you are.")).toBeInTheDocument();
    expect(
      screen.getByText("I need an email address to reply to."),
    ).toBeInTheDocument();
    expect(submitContactMessage).not.toHaveBeenCalled();
  });

  it("marks an invalid field and ties the message to it", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("Email"), "not-an-email");
    await send(user);

    const email = screen.getByLabelText("Email");
    await waitFor(() => expect(email).toHaveAttribute("aria-invalid", "true"));
    expect(email).toHaveAccessibleDescription(
      "That doesn’t look like an email address.",
    );
  });

  it("rejects a message under ten characters", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("Message"), "too short");
    await send(user);

    expect(
      await screen.findByText("A few more words — at least 10 characters."),
    ).toBeInTheDocument();
    expect(submitContactMessage).not.toHaveBeenCalled();
  });

  it("sends the trimmed values and the widget's token", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await user.type(screen.getByLabelText("Subject (optional)"), "A role");
    await send(user);

    await waitFor(() => expect(submitContactMessage).toHaveBeenCalledTimes(1));
    expect(submitContactMessage).toHaveBeenCalledWith(
      {
        name: "Ada Lovelace",
        email: "ada@example.com",
        subject: "A role",
        message: "I would like to talk to you about a backend role.",
        website: "",
      },
      "test-widget-token",
    );
  });

  it("replaces the form with a confirmation once it has sent", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    expect(
      await screen.findByRole("heading", { name: "Message sent" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Queued");
    expect(screen.queryByLabelText("Full name")).not.toBeInTheDocument();
  });

  it("comes back to an empty form from the confirmation", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);
    await user.click(
      await screen.findByRole("button", { name: /send another/i }),
    );

    expect(screen.getByLabelText("Full name")).toHaveValue("");
    expect(screen.getByLabelText("Message")).toHaveValue("");
  });

  it("asks the widget for a fresh token after every attempt", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    // Turnstile tokens are single-use, so a spent one must never be reused.
    await waitFor(() => expect(resetWidget).toHaveBeenCalled());
  });

  it("puts server field errors back on the fields they name", async () => {
    submitContactMessage.mockResolvedValue({
      ok: false,
      kind: "fields",
      message: "One or more fields are invalid.",
      fieldErrors: { email: "must be a valid email address" },
    } satisfies ContactSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    expect(
      await screen.findByText("must be a valid email address"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it("explains a spent rate limit with the wait the server gave", async () => {
    submitContactMessage.mockResolvedValue({
      ok: false,
      kind: "rateLimit",
      message: "Too many messages from here — try again in about 30 minutes.",
    } satisfies ContactSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Too many messages");
    expect(alert).toHaveTextContent("try again in about 30 minutes");
  });

  it("explains a failed bot check and keeps the typed message", async () => {
    submitContactMessage.mockResolvedValue({
      ok: false,
      kind: "turnstile",
      message: "That bot check didn’t go through. Complete it again, then resend.",
    } satisfies ContactSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Couldn’t verify you’re human");
    // Nothing the visitor wrote is thrown away by a failure they can retry.
    expect(screen.getByLabelText("Message")).toHaveValue(
      "I would like to talk to you about a backend role.",
    );
  });

  it("explains an unreachable server", async () => {
    submitContactMessage.mockResolvedValue({
      ok: false,
      kind: "unknown",
      message: "Couldn’t reach the server. Check your connection and resend.",
    } satisfies ContactSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Message not sent",
    );
  });

  it("moves focus to the banner so it is not missed", async () => {
    submitContactMessage.mockResolvedValue({
      ok: false,
      kind: "unknown",
      message: "Something went wrong.",
    } satisfies ContactSubmitResult);

    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveFocus(),
    );
  });

  it("says so instead of submitting when the bot check has not finished", async () => {
    solveTurnstile = false;
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await send(user);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Still checking you’re human",
    );
    expect(submitContactMessage).not.toHaveBeenCalled();
  });

  it("keeps the submit button usable while the check is still running", async () => {
    solveTurnstile = false;
    renderForm();

    // A dead button with no explanation is worse than one that explains itself
    // on press, so it is never disabled for a missing token.
    expect(screen.getByRole("button", { name: /send message/i })).toBeEnabled();
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
    // Out of the accessibility tree: a screen reader is offered the four real
    // fields and never this one.
    expect(screen.getAllByRole("textbox")).toHaveLength(4);
    expect(screen.getAllByRole("textbox")).not.toContain(honeypot);
  });

  it("names the form for assistive technology", () => {
    renderForm();

    expect(screen.getByRole("form", { name: "Contact form" })).toBeInTheDocument();
  });
});
