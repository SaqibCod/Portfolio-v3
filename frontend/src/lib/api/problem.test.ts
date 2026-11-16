import { describe, expect, it } from "vitest";

import {
  isProblem,
  problemFieldErrors,
  problemMessage,
  type Problem,
} from "@/lib/api/problem";

const validation: Problem = {
  type: "https://api.example.com/problems/validation",
  title: "Validation failed",
  status: 400,
  detail: "Your message is too long.",
  errors: [
    { field: "message", message: "must be at most 500 characters" },
    { field: "message", message: "another message problem" },
    { field: "email", message: "must be a well-formed email address" },
  ],
};

describe("isProblem", () => {
  it("accepts a value with the required Problem fields", () => {
    expect(isProblem(validation)).toBe(true);
  });

  it("rejects anything missing type, title or status", () => {
    expect(isProblem({ title: "No type", status: 400 })).toBe(false);
    expect(isProblem({ type: "x", title: "No status" })).toBe(false);
    expect(isProblem({ type: "x", title: "y", status: "400" })).toBe(false);
  });

  it("rejects non-objects", () => {
    expect(isProblem(null)).toBe(false);
    expect(isProblem(undefined)).toBe(false);
    expect(isProblem("boom")).toBe(false);
  });
});

describe("problemMessage", () => {
  it("prefers the server's detail", () => {
    expect(problemMessage(validation)).toBe("Your message is too long.");
  });

  it("falls back to a message for the status code", () => {
    const rateLimited: Problem = {
      type: "https://api.example.com/problems/rate-limit",
      title: "Too many requests",
      status: 429,
    };

    expect(problemMessage(rateLimited)).toBe(
      "Too many requests. Wait a moment, then try again.",
    );
  });

  it("falls back to the title for an unmapped status", () => {
    expect(
      problemMessage({
        type: "https://api.example.com/problems/teapot",
        title: "I'm a teapot",
        status: 418,
      }),
    ).toBe("I'm a teapot");
  });

  it("ignores a blank detail", () => {
    expect(
      problemMessage({
        type: "https://api.example.com/problems/not-found",
        title: "Not found",
        status: 404,
        detail: "   ",
      }),
    ).toBe("We couldn't find what you were looking for.");
  });

  it("returns a generic message for anything that is not a Problem", () => {
    expect(problemMessage(new TypeError("fetch failed"))).toBe(
      "Something went wrong. Please try again.",
    );
    expect(problemMessage(undefined)).toBe(
      "Something went wrong. Please try again.",
    );
  });
});

describe("problemFieldErrors", () => {
  it("keys the first message per field", () => {
    expect(problemFieldErrors(validation)).toEqual({
      message: "must be at most 500 characters",
      email: "must be a well-formed email address",
    });
  });

  it("returns an empty map when there are no field errors", () => {
    expect(
      problemFieldErrors({
        type: "https://api.example.com/problems/rate-limit",
        title: "Too many requests",
        status: 429,
      }),
    ).toEqual({});
    expect(problemFieldErrors("nope")).toEqual({});
  });
});
