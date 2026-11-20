import { describe, expect, it } from "vitest";

import { avatarToken } from "@/components/guestbook/GuestbookEntryCard";
import { guestbookSchema, rateLimitMessage } from "@/lib/guestbook";

describe("guestbookSchema", () => {
  it("accepts a note at the contract's bounds", () => {
    const result = guestbookSchema.safeParse({
      name: "P",
      message: "hi",
    });

    expect(result.success).toBe(true);
  });

  it("accepts the longest note the contract allows", () => {
    const result = guestbookSchema.safeParse({
      name: "n".repeat(50),
      message: "m".repeat(500),
    });

    expect(result.success).toBe(true);
  });

  it("refuses a name that is only whitespace", () => {
    const result = guestbookSchema.safeParse({ name: "   ", message: "hi" });

    expect(result.success).toBe(false);
  });

  it("refuses a one-character message, as the API does", () => {
    const result = guestbookSchema.safeParse({ name: "Priya", message: "k" });

    expect(result.success).toBe(false);
  });

  it("refuses values past the column widths", () => {
    expect(
      guestbookSchema.safeParse({ name: "n".repeat(51), message: "hi" }).success,
    ).toBe(false);
    expect(
      guestbookSchema.safeParse({ name: "Priya", message: "m".repeat(501) })
        .success,
    ).toBe(false);
  });

  it("trims what it hands on, so the API never stores padding", () => {
    const result = guestbookSchema.safeParse({
      name: "  Priya  ",
      message: "  Loved it.  ",
    });

    expect(result.success).toBe(true);
    expect(result.data?.name).toBe("Priya");
    expect(result.data?.message).toBe("Loved it.");
  });

  it("treats the honeypot as optional and never validates it", () => {
    // A 400 for a filled-in honeypot would tell a bot it had been spotted.
    const result = guestbookSchema.safeParse({
      name: "Spam Bot",
      message: "Buy our product.",
      website: "https://spam.example",
    });

    expect(result.success).toBe(true);
  });
});

describe("rateLimitMessage", () => {
  it("speaks in hours, which is the unit a two-a-day limit needs", () => {
    expect(rateLimitMessage(14 * 3600)).toContain("about 14 hours");
  });

  it("rounds up, so it never suggests retrying immediately", () => {
    expect(rateLimitMessage(3601)).toContain("about 2 hours");
    expect(rateLimitMessage(61)).toContain("about 2 minutes");
  });

  it("uses minutes for a short wait", () => {
    expect(rateLimitMessage(120)).toContain("about 2 minutes");
  });

  it("says a singular hour and minute properly", () => {
    expect(rateLimitMessage(3600)).toContain("about an hour");
    expect(rateLimitMessage(30)).toContain("about a minute");
  });

  it("falls back to vague wording with no usable header", () => {
    expect(rateLimitMessage(null)).toBe(
      "That is the limit for now — try again a little later.",
    );
    expect(rateLimitMessage(Number.NaN)).toContain("a little later");
  });
});

describe("avatarToken", () => {
  it("gives the same id the same colour every time", () => {
    const id = "11111111-2222-3333-4444-555555555555";

    expect(avatarToken(id)).toBe(avatarToken(id));
  });

  it("only ever returns one of the palette tokens", () => {
    const tokens = new Set(
      Array.from({ length: 50 }, (_, index) =>
        avatarToken(`entry-${index}-aaaa-bbbb-cccc`),
      ),
    );

    for (const token of tokens) {
      expect(token).toMatch(/^var\(--av-[1-6]\)$/);
    }
  });

  it("spreads ids across the whole palette", () => {
    const tokens = new Set(
      Array.from({ length: 60 }, (_, index) =>
        avatarToken(`${index}-0000-0000-0000-000000000000`),
      ),
    );

    // All six, so a list of notes is not one flat colour.
    expect(tokens.size).toBe(6);
  });
});
