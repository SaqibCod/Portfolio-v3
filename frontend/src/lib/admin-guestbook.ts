import { apiClient } from "@/lib/api/client";
import { problemMessage } from "@/lib/api/problem";

import type { components } from "@/lib/api/schema";

/** One entry as the owner sees it, exactly as the contract defines it. */
export type AdminGuestbookEntry = components["schemas"]["AdminGuestbookEntry"];

/** One page of entries for moderation. */
export type AdminGuestbookPage = components["schemas"]["AdminGuestbookPage"];

/** The three statuses an entry can be in, as the contract spells them. */
export type ModerationStatus = AdminGuestbookEntry["status"];

/** The two an entry can be moved to. "Un-decide" is deliberately not one. */
export type ModerationDecision = components["schemas"]["ModerateEntry"]["status"];

export const MODERATION_STATUSES: readonly ModerationStatus[] = [
  "pending",
  "approved",
  "rejected",
];

/** The contract's ceiling. The owner wants the whole queue, not a page of it. */
export const ADMIN_PAGE_SIZE = 50;

/**
 * What every admin call can come back as.
 *
 * `unauthorized` is separated from the other failures because it is the only
 * one the page reacts to structurally: the token is wrong or has been rotated,
 * so the gate has to close again rather than just showing a message.
 */
export type AdminResult<T> =
  | { ok: true; data: T }
  | { ok: false; kind: "unauthorized"; message: string }
  | { ok: false; kind: "notFound"; message: string }
  | { ok: false; kind: "unknown"; message: string };

/**
 * The owner's token as an `Authorization` header, and nothing else.
 *
 * Never a query parameter and never a cookie: a query string ends up in
 * browser history, in `Referer` headers and in any access log the request
 * passes through, and a cookie would outlive the tab.
 */
function auth(token: string) {
  return { Authorization: `Bearer ${token}` };
}

function failure<T>(
  status: number,
  error: unknown,
  fallback: string,
): AdminResult<T> {
  if (status === 401) {
    return {
      ok: false,
      kind: "unauthorized",
      message: "Token rejected (401). Check it and try again.",
    };
  }
  if (status === 404) {
    return {
      ok: false,
      kind: "notFound",
      message: "That entry is already gone.",
    };
  }
  return {
    ok: false,
    kind: "unknown",
    message: status === 0 ? fallback : problemMessage(error),
  };
}

const UNREACHABLE = "Couldn’t reach the server. Check your connection.";

/**
 * One page of entries with the given status.
 *
 * `cache: "no-store"` throughout this module: moderation is the one place on
 * the site where a stale answer is actively misleading — the owner would be
 * deciding on a queue that has already changed.
 */
export async function listForModeration(
  token: string,
  status: ModerationStatus,
  cursor?: string,
): Promise<AdminResult<AdminGuestbookPage>> {
  try {
    const { data, error, response } = await apiClient.GET(
      "/api/v1/admin/guestbook",
      {
        params: {
          query: { status, limit: ADMIN_PAGE_SIZE, ...(cursor ? { cursor } : {}) },
        },
        headers: auth(token),
        cache: "no-store",
      },
    );

    if (data) return { ok: true, data };
    return failure(response?.status ?? 0, error, UNREACHABLE);
  } catch {
    return { ok: false, kind: "unknown", message: UNREACHABLE };
  }
}

/** Approves or rejects one entry, returning it as it now stands. */
export async function moderateEntry(
  token: string,
  entryId: string,
  status: ModerationDecision,
): Promise<AdminResult<AdminGuestbookEntry>> {
  try {
    const { data, error, response } = await apiClient.PATCH(
      "/api/v1/admin/guestbook/{entryId}",
      {
        params: { path: { entryId } },
        body: { status },
        headers: auth(token),
        cache: "no-store",
      },
    );

    if (data) return { ok: true, data };
    return failure(response?.status ?? 0, error, UNREACHABLE);
  } catch {
    return { ok: false, kind: "unknown", message: UNREACHABLE };
  }
}

/** Deletes one entry for good. 204, so there is nothing to return. */
export async function deleteEntry(
  token: string,
  entryId: string,
): Promise<AdminResult<null>> {
  try {
    const { error, response } = await apiClient.DELETE(
      "/api/v1/admin/guestbook/{entryId}",
      {
        params: { path: { entryId } },
        headers: auth(token),
        cache: "no-store",
      },
    );

    if (response?.status === 204) return { ok: true, data: null };
    return failure(response?.status ?? 0, error, UNREACHABLE);
  } catch {
    return { ok: false, kind: "unknown", message: UNREACHABLE };
  }
}

/** The first page of each status, which is what the moderation page opens with. */
export type ModerationPages = Record<ModerationStatus, AdminGuestbookPage>;

/**
 * Checks a token and loads the first page of all three statuses.
 *
 * There is no "verify this token" endpoint in the contract, and adding one
 * would be a second place for the token to be accepted — so the check is the
 * first real request instead. All three statuses, because the page shows all
 * three counts from its first paint, and in parallel because they do not
 * depend on each other.
 *
 * Any one of them answering 401 fails the whole unlock: a token that cannot
 * read every status is not a token worth holding.
 */
export async function openModerationQueue(
  token: string,
): Promise<AdminResult<ModerationPages>> {
  const results = await Promise.all(
    MODERATION_STATUSES.map((status) => listForModeration(token, status)),
  );

  const failure = results.find((result) => !result.ok);
  if (failure && !failure.ok) return failure;

  const pages = {} as ModerationPages;
  MODERATION_STATUSES.forEach((status, index) => {
    const result = results[index];
    if (result.ok) pages[status] = result.data;
  });
  return { ok: true, data: pages };
}
