import { z } from "zod";

import { apiClient } from "@/lib/api/client";
import { isProblem, problemFieldErrors, problemMessage } from "@/lib/api/problem";

/**
 * The contact form's shape and limits.
 *
 * Every bound mirrors the `ContactRequest` schema in
 * `docs/api/portfolio-api.yaml`, so the client refuses exactly what the server
 * would refuse. This is a courtesy, never a guarantee: the API validates the
 * same rules again, and its answer is the one that counts.
 *
 * `turnstileToken` is absent on purpose — it comes from the widget, not from
 * anything a visitor types, so it is passed alongside these values instead.
 */
export const contactSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Tell me who you are.")
    .max(100, "That has to fit in 100 characters."),
  email: z
    .string()
    .trim()
    .min(1, "I need an email address to reply to.")
    .max(254, "That has to fit in 254 characters.")
    .pipe(z.email("That doesn’t look like an email address.")),
  subject: z
    .string()
    .trim()
    .max(150, "That has to fit in 150 characters.")
    .optional(),
  message: z
    .string()
    .trim()
    .min(10, "A few more words — at least 10 characters.")
    .max(5000, "That has to fit in 5000 characters."),
  /**
   * Honeypot. Hidden from people, so a real visitor always leaves it empty and
   * a bot usually does not. Deliberately unvalidated: the server decides what
   * a filled-in value means, and it answers 202 either way so a bot never
   * learns it was caught.
   */
  website: z.string().optional(),
});

export type ContactFormValues = z.infer<typeof contactSchema>;

/** Field names the API can report errors against, as the contract spells them. */
export type ContactFieldName = "name" | "email" | "subject" | "message";

const CONTACT_FIELDS: readonly ContactFieldName[] = [
  "name",
  "email",
  "subject",
  "message",
];

export type ContactSubmitResult =
  | { ok: true; id: string }
  /** 400 — one or more fields were rejected; `fieldErrors` is keyed by field name. */
  | {
      ok: false;
      kind: "fields";
      message: string;
      fieldErrors: Partial<Record<ContactFieldName, string>>;
    }
  /** 422, or a 400 naming `turnstileToken` — the bot check has to be redone. */
  | { ok: false; kind: "turnstile"; message: string }
  /** 429 — the per-IP hourly budget is spent. */
  | { ok: false; kind: "rateLimit"; message: string }
  /** A network failure, a 503, or anything else unexpected. */
  | { ok: false; kind: "unknown"; message: string };

/**
 * Turns a `Retry-After` value into the one sentence the form shows.
 *
 * The header is in seconds, which is not what anyone wants to read, and
 * rounding up matters: telling someone to wait 59 seconds as "0 minutes"
 * would invite an immediate, failing retry.
 */
export function rateLimitMessage(retryAfterSeconds: number | null): string {
  if (retryAfterSeconds === null || !Number.isFinite(retryAfterSeconds)) {
    return "Too many messages from here. Try again a little later.";
  }

  const minutes = Math.ceil(Math.max(retryAfterSeconds, 1) / 60);
  const when =
    minutes <= 1 ? "in about a minute" : `in about ${minutes} minutes`;
  return `Too many messages from here — try again ${when}.`;
}

function retryAfterSeconds(response: Response | undefined): number | null {
  const header = response?.headers.get("Retry-After");
  if (!header) return null;
  const seconds = Number.parseInt(header, 10);
  return Number.isNaN(seconds) ? null : seconds;
}

/** Keeps only the errors that name a field this form actually renders. */
function contactFieldErrors(
  all: Record<string, string>,
): Partial<Record<ContactFieldName, string>> {
  const result: Partial<Record<ContactFieldName, string>> = {};
  for (const field of CONTACT_FIELDS) {
    if (all[field]) result[field] = all[field];
  }
  return result;
}

const TURNSTILE_RETRY_MESSAGE =
  "That bot check didn’t go through. Complete it again, then resend.";

/**
 * Posts one message to `POST /api/v1/contact`.
 *
 * Every failure is normalised into a `ContactSubmitResult` so the component
 * decides what to show without re-reading status codes or problem shapes. A
 * thrown error is caught here too: the form must never break the page.
 */
export async function submitContactMessage(
  values: ContactFormValues,
  turnstileToken: string,
): Promise<ContactSubmitResult> {
  let data;
  let error;
  let response: Response | undefined;

  try {
    ({ data, error, response } = await apiClient.POST("/api/v1/contact", {
      body: {
        name: values.name,
        email: values.email,
        // An empty subject is omitted rather than sent as "", which the
        // contract treats as a 0-length string and the server would store.
        ...(values.subject ? { subject: values.subject } : {}),
        message: values.message,
        turnstileToken,
        ...(values.website ? { website: values.website } : {}),
      },
    }));
  } catch {
    return {
      ok: false,
      kind: "unknown",
      message: "Couldn’t reach the server. Check your connection and resend.",
    };
  }

  if (data) {
    return { ok: true, id: data.id };
  }

  const status = response?.status ?? 0;
  const message = problemMessage(error);

  if (status === 429) {
    return {
      ok: false,
      kind: "rateLimit",
      message: rateLimitMessage(retryAfterSeconds(response)),
    };
  }

  if (status === 422) {
    return { ok: false, kind: "turnstile", message: TURNSTILE_RETRY_MESSAGE };
  }

  if (status === 400 && isProblem(error)) {
    const all = problemFieldErrors(error);
    // A rejected token arrives as a field error rather than a 422 when the
    // request never carried one at all; it is still the bot check that failed.
    if (all.turnstileToken) {
      return { ok: false, kind: "turnstile", message: TURNSTILE_RETRY_MESSAGE };
    }
    const fieldErrors = contactFieldErrors(all);
    if (Object.keys(fieldErrors).length > 0) {
      return { ok: false, kind: "fields", message, fieldErrors };
    }
  }

  return { ok: false, kind: "unknown", message };
}
