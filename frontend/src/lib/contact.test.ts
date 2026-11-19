import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "@/lib/api/client";
import {
  contactSchema,
  rateLimitMessage,
  submitContactMessage,
  type ContactFormValues,
} from "@/lib/contact";

const valid: ContactFormValues = {
  name: "Ada Lovelace",
  email: "ada@example.com",
  subject: "A role for you",
  message: "I would like to talk to you about a backend role.",
  website: "",
};

/** Builds what `openapi-fetch` hands back for a failed request. */
function problemResponse(
  status: number,
  body: unknown,
  headers: Record<string, string> = {},
) {
  return {
    data: undefined,
    error: body,
    response: new Response(null, { status, headers }),
  };
}

let post: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  post = vi.spyOn(apiClient, "POST");
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("contactSchema", () => {
  it("accepts a filled-in form", () => {
    expect(contactSchema.safeParse(valid).success).toBe(true);
  });

  it("accepts a form with no subject", () => {
    const { subject, ...withoutSubject } = valid;
    expect(subject).toBeDefined();
    expect(contactSchema.safeParse(withoutSubject).success).toBe(true);
  });

  it("requires a name", () => {
    const result = contactSchema.safeParse({ ...valid, name: "   " });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe("Tell me who you are.");
  });

  it("requires a real email address", () => {
    const result = contactSchema.safeParse({ ...valid, email: "nope" });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe(
      "That doesn’t look like an email address.",
    );
  });

  it("asks for at least ten characters of message", () => {
    const result = contactSchema.safeParse({ ...valid, message: "too short" });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe(
      "A few more words — at least 10 characters.",
    );
  });

  it("mirrors every upper bound in the contract", () => {
    expect(
      contactSchema.safeParse({ ...valid, name: "n".repeat(101) }).success,
    ).toBe(false);
    expect(
      contactSchema.safeParse({ ...valid, subject: "s".repeat(151) }).success,
    ).toBe(false);
    expect(
      contactSchema.safeParse({ ...valid, message: "m".repeat(5001) }).success,
    ).toBe(false);
    expect(
      contactSchema.safeParse({ ...valid, name: "n".repeat(100) }).success,
    ).toBe(true);
  });

  it("never rejects a filled-in honeypot, so a bot is not told it was caught", () => {
    expect(
      contactSchema.safeParse({ ...valid, website: "https://spam.example" })
        .success,
    ).toBe(true);
  });

  it("trims what it accepts", () => {
    const result = contactSchema.parse({ ...valid, name: "  Ada  " });
    expect(result.name).toBe("Ada");
  });
});

describe("rateLimitMessage", () => {
  it("rounds up to whole minutes", () => {
    expect(rateLimitMessage(61)).toBe(
      "Too many messages from here — try again in about 2 minutes.",
    );
  });

  it("never says zero minutes", () => {
    expect(rateLimitMessage(30)).toBe(
      "Too many messages from here — try again in about a minute.",
    );
    expect(rateLimitMessage(0)).toBe(
      "Too many messages from here — try again in about a minute.",
    );
  });

  it("falls back when the server sent no Retry-After", () => {
    expect(rateLimitMessage(null)).toBe(
      "Too many messages from here. Try again a little later.",
    );
  });
});

describe("submitContactMessage", () => {
  it("posts the form and reports the id it gets back", async () => {
    post.mockResolvedValue({
      data: { id: "11111111-2222-3333-4444-555555555555", status: "queued" },
      error: undefined,
      response: new Response(null, { status: 202 }),
    });

    const result = await submitContactMessage(valid, "widget-token");

    expect(result).toEqual({
      ok: true,
      id: "11111111-2222-3333-4444-555555555555",
    });
    expect(post).toHaveBeenCalledWith("/api/v1/contact", {
      body: {
        name: "Ada Lovelace",
        email: "ada@example.com",
        subject: "A role for you",
        message: "I would like to talk to you about a backend role.",
        turnstileToken: "widget-token",
      },
    });
  });

  it("omits an empty subject rather than sending a blank one", async () => {
    post.mockResolvedValue({
      data: { id: "id", status: "queued" },
      error: undefined,
      response: new Response(null, { status: 202 }),
    });

    await submitContactMessage({ ...valid, subject: "" }, "widget-token");

    expect(post.mock.calls[0]?.[1]?.body).not.toHaveProperty("subject");
  });

  it("forwards a filled-in honeypot so the server can drop the message", async () => {
    post.mockResolvedValue({
      data: { id: "id", status: "queued" },
      error: undefined,
      response: new Response(null, { status: 202 }),
    });

    await submitContactMessage(
      { ...valid, website: "https://spam.example" },
      "widget-token",
    );

    expect(post.mock.calls[0]?.[1]?.body).toMatchObject({
      website: "https://spam.example",
    });
  });

  it("maps a 400 onto the fields it names", async () => {
    post.mockResolvedValue(
      problemResponse(400, {
        type: "https://api.example.com/problems/validation",
        title: "Validation failed",
        status: 400,
        detail: "One or more fields are invalid.",
        errors: [
          { field: "email", message: "must be a valid email address" },
          { field: "message", message: "must be between 10 and 5000 characters" },
        ],
      }),
    );

    const result = await submitContactMessage(valid, "widget-token");

    expect(result).toEqual({
      ok: false,
      kind: "fields",
      message: "One or more fields are invalid.",
      fieldErrors: {
        email: "must be a valid email address",
        message: "must be between 10 and 5000 characters",
      },
    });
  });

  it("treats a rejected token as a bot-check failure, not a field error", async () => {
    post.mockResolvedValue(
      problemResponse(400, {
        type: "https://api.example.com/problems/validation",
        title: "Validation failed",
        status: 400,
        errors: [{ field: "turnstileToken", message: "is required" }],
      }),
    );

    const result = await submitContactMessage(valid, "");

    expect(result).toMatchObject({ ok: false, kind: "turnstile" });
  });

  it("maps a 422 onto the bot check", async () => {
    post.mockResolvedValue(
      problemResponse(422, {
        type: "https://api.example.com/problems/turnstile-failed",
        title: "Turnstile verification failed",
        status: 422,
      }),
    );

    const result = await submitContactMessage(valid, "stale-token");

    expect(result).toEqual({
      ok: false,
      kind: "turnstile",
      message: "That bot check didn’t go through. Complete it again, then resend.",
    });
  });

  it("reads the wait out of Retry-After on a 429", async () => {
    post.mockResolvedValue(
      problemResponse(
        429,
        {
          type: "https://api.example.com/problems/rate-limited",
          title: "Too many requests",
          status: 429,
        },
        { "Retry-After": "1800" },
      ),
    );

    const result = await submitContactMessage(valid, "widget-token");

    expect(result).toEqual({
      ok: false,
      kind: "rateLimit",
      message: "Too many messages from here — try again in about 30 minutes.",
    });
  });

  it("falls back to a generic wait when a 429 carries no Retry-After", async () => {
    post.mockResolvedValue(
      problemResponse(429, {
        type: "https://api.example.com/problems/rate-limited",
        title: "Too many requests",
        status: 429,
      }),
    );

    const result = await submitContactMessage(valid, "widget-token");

    expect(result).toMatchObject({
      kind: "rateLimit",
      message: "Too many messages from here. Try again a little later.",
    });
  });

  it("passes a 503 through as the server's own sentence", async () => {
    post.mockResolvedValue(
      problemResponse(503, {
        type: "https://api.example.com/problems/upstream-unavailable",
        title: "Service unavailable",
        status: 503,
        detail: "Turnstile verification is unavailable.",
      }),
    );

    const result = await submitContactMessage(valid, "widget-token");

    expect(result).toEqual({
      ok: false,
      kind: "unknown",
      message: "Turnstile verification is unavailable.",
    });
  });

  it("survives a network failure", async () => {
    post.mockRejectedValue(new TypeError("Failed to fetch"));

    const result = await submitContactMessage(valid, "widget-token");

    expect(result).toEqual({
      ok: false,
      kind: "unknown",
      message: "Couldn’t reach the server. Check your connection and resend.",
    });
  });
});
