import { z } from "zod";

import { apiClient } from "@/lib/api/client";
import {
  isProblem,
  problemFieldErrors,
  problemMessage,
} from "@/lib/api/problem";

import type { components } from "@/lib/api/schema";

/** One approved entry, exactly as the contract defines it. */
export type GuestbookEntry = components["schemas"]["GuestbookEntry"];

/** One page of approved entries: the items plus the cursor for the next page. */
export type GuestbookPage = components["schemas"]["GuestbookPage"];

/**
 * The guestbook form's shape and limits.
 *
 * Every bound mirrors the `CreateGuestbookEntry` schema in
 * `docs/api/portfolio-api.yaml`, so the client refuses exactly what the server
 * would refuse. This is a courtesy, never a guarantee: the API validates the
 * same rules again, and its answer is the one that counts.
 *
 * `turnstileToken` is absent on purpose — it comes from the widget, not from
 * anything a visitor types, so it is passed alongside these values instead.
 */
export const guestbookSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Tell me how to credit you.")
    .max(50, "That has to fit in 50 characters."),
  message: z
    .string()
    .trim()
    .min(2, "Message must be at least 2 characters.")
    .max(500, "That has to fit in 500 characters."),
  /**
   * Honeypot. Hidden from people, so a real visitor always leaves it empty and
   * a bot usually does not. Deliberately unvalidated: the server decides what
   * a filled-in value means, and it answers 202 either way so a bot never
   * learns it was caught.
   */
  website: z.string().optional(),
});

export type GuestbookFormValues = z.infer<typeof guestbookSchema>;

/** Field names the API can report errors against, as the contract spells them. */
export type GuestbookFieldName = "name" | "message";

const GUESTBOOK_FIELDS: readonly GuestbookFieldName[] = ["name", "message"];

export type GuestbookSubmitResult =
  | { ok: true; id: string }
  /** 400 — one or more fields were rejected; `fieldErrors` is keyed by field name. */
  | {
      ok: false;
      kind: "fields";
      message: string;
      fieldErrors: Partial<Record<GuestbookFieldName, string>>;
    }
  /** 422, or a 400 naming `turnstileToken` — the bot check has to be redone. */
  | { ok: false; kind: "turnstile"; message: string }
  /** 429 — the two-a-day budget for this address is spent. */
  | { ok: false; kind: "rateLimit"; message: string }
  /** A network failure, a 503, or anything else unexpected. */
  | { ok: false; kind: "unknown"; message: string };

/**
 * Turns a `Retry-After` value into the one sentence the form shows.
 *
 * The limit here is two entries a day, so the useful unit is hours rather than
 * the minutes the contact form talks in. Rounding up matters: telling someone
 * to wait "0 hours" would invite an immediate, failing retry.
 */
export function rateLimitMessage(retryAfterSeconds: number | null): string {
  if (retryAfterSeconds === null || !Number.isFinite(retryAfterSeconds)) {
    return "That is the limit for now — try again a little later.";
  }

  const seconds = Math.max(retryAfterSeconds, 1);
  if (seconds < 3600) {
    const minutes = Math.ceil(seconds / 60);
    return minutes <= 1
      ? "That is the limit for now — try again in about a minute."
      : `That is the limit for now — try again in about ${minutes} minutes.`;
  }

  const hours = Math.ceil(seconds / 3600);
  return hours <= 1
    ? "That is the limit for now — try again in about an hour."
    : `That is the limit for now — try again in about ${hours} hours.`;
}

function retryAfterSeconds(response: Response | undefined): number | null {
  const header = response?.headers.get("Retry-After");
  if (!header) return null;
  const seconds = Number.parseInt(header, 10);
  return Number.isNaN(seconds) ? null : seconds;
}

/** Keeps only the errors that name a field this form actually renders. */
function guestbookFieldErrors(
  all: Record<string, string>,
): Partial<Record<GuestbookFieldName, string>> {
  const result: Partial<Record<GuestbookFieldName, string>> = {};
  for (const field of GUESTBOOK_FIELDS) {
    if (all[field]) result[field] = all[field];
  }
  return result;
}

const TURNSTILE_RETRY_MESSAGE =
  "Please complete the check again, then resend.";

/**
 * Posts one note to `POST /api/v1/guestbook`.
 *
 * Client-side rather than a Server Action, for the same reason as the contact
 * form: the API applies a per-IP limit and passes the caller's address to
 * Cloudflare, and proxying through Next would hand it the server's address
 * instead, making every visitor look like the same one.
 *
 * Every failure is normalised into a `GuestbookSubmitResult` so the component
 * decides what to show without re-reading status codes or problem shapes.
 */
export async function submitGuestbookEntry(
  values: GuestbookFormValues,
  turnstileToken: string,
): Promise<GuestbookSubmitResult> {
  let data;
  let error;
  let response: Response | undefined;

  try {
    ({ data, error, response } = await apiClient.POST("/api/v1/guestbook", {
      body: {
        name: values.name,
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
    const fieldErrors = guestbookFieldErrors(all);
    if (Object.keys(fieldErrors).length > 0) {
      return { ok: false, kind: "fields", message, fieldErrors };
    }
  }

  return { ok: false, kind: "unknown", message };
}

/** The contract's `limit` default, and the page size the site asks for. */
export const GUESTBOOK_PAGE_SIZE = 20;

/**
 * How long a server-rendered page of entries may be reused, in seconds.
 *
 * A guestbook changes only when the owner approves something, so a minute of
 * staleness costs nothing and keeps the page static for almost every visitor.
 */
export const GUESTBOOK_REVALIDATE_SECONDS = 60;

/**
 * The first page of approved entries, for the server-rendered list.
 *
 * `next: { revalidate }` puts the response in Next's data cache, so `/guestbook`
 * is served as static HTML and re-fetched at most once a minute rather than on
 * every request.
 *
 * Returns `null` when the API cannot be reached. The page is deliberately built
 * to render without it: an unreachable backend must not take out a route whose
 * whole job is to be indexable.
 */
export async function fetchApprovedEntries(
  limit = GUESTBOOK_PAGE_SIZE,
): Promise<GuestbookPage | null> {
  try {
    const { data } = await apiClient.GET("/api/v1/guestbook", {
      params: { query: { limit } },
      next: { revalidate: GUESTBOOK_REVALIDATE_SECONDS },
    });
    return data ?? null;
  } catch {
    return null;
  }
}

export type LoadMoreResult =
  | { ok: true; page: GuestbookPage }
  | { ok: false; message: string };

/**
 * The next page of approved entries, for the "Load more" button.
 *
 * Uncached, unlike {@link fetchApprovedEntries}: this runs in the browser in
 * response to a click, where a minute-old answer would silently re-append rows
 * the visitor is already looking at.
 */
export async function fetchMoreApprovedEntries(
  cursor: string,
  limit = GUESTBOOK_PAGE_SIZE,
): Promise<LoadMoreResult> {
  try {
    const { data, error } = await apiClient.GET("/api/v1/guestbook", {
      params: { query: { cursor, limit } },
      cache: "no-store",
    });

    if (!data) {
      return { ok: false, message: problemMessage(error) };
    }
    return { ok: true, page: data };
  } catch {
    return {
      ok: false,
      message: "Couldn’t load more notes. Check your connection and retry.",
    };
  }
}
