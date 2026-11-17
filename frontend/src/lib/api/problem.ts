import type { components } from "@/lib/api/schema";

/** RFC 9457 Problem Details, exactly as the contract defines it. */
export type Problem = components["schemas"]["Problem"];

/** A single field-level error carried by a validation problem. */
export type ProblemFieldError = NonNullable<Problem["errors"]>[number];

/**
 * Narrows an unknown `error` from `openapi-fetch` to a `Problem`.
 *
 * Every non-2xx response from the API is `application/problem+json`, but the
 * value still arrives as `unknown` when a request fails before parsing (a
 * network error, for instance), so always guard before reading fields.
 */
export function isProblem(value: unknown): value is Problem {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.type === "string" &&
    typeof candidate.title === "string" &&
    typeof candidate.status === "number"
  );
}

const FALLBACK_MESSAGE = "Something went wrong. Please try again.";

const STATUS_MESSAGES: Record<number, string> = {
  400: "That request wasn't valid. Check the highlighted fields and try again.",
  401: "You need to sign in to do that.",
  403: "You don't have permission to do that.",
  404: "We couldn't find what you were looking for.",
  409: "That conflicts with something that already exists.",
  422: "That request wasn't valid. Check the highlighted fields and try again.",
  429: "Too many requests. Wait a moment, then try again.",
  503: "That service is temporarily unavailable. Try again shortly.",
};

/**
 * Turns an API error into one sentence a visitor can act on.
 *
 * Prefers the server's `detail` (written for humans), then a message keyed to
 * the status code, then the problem `title`.
 */
export function problemMessage(value: unknown): string {
  if (!isProblem(value)) return FALLBACK_MESSAGE;

  const detail = value.detail?.trim();
  if (detail) return detail;

  const byStatus = STATUS_MESSAGES[value.status];
  if (byStatus) return byStatus;

  const title = value.title.trim();
  return title || FALLBACK_MESSAGE;
}

/**
 * Field errors keyed by field name, for wiring validation problems back into a
 * form. Empty when the problem carries no field-level errors.
 */
export function problemFieldErrors(value: unknown): Record<string, string> {
  if (!isProblem(value) || !value.errors) return {};

  const result: Record<string, string> = {};
  for (const { field, message } of value.errors) {
    // Keep the first message per field; the API lists them most-specific first.
    result[field] ??= message;
  }
  return result;
}
