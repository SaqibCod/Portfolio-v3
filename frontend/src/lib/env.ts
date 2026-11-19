import { z } from "zod";

/**
 * Public environment. Every variable here is inlined into the client bundle by
 * Next, so only `NEXT_PUBLIC_*` names belong in this file.
 *
 * Parsing happens at module load, which means a missing or malformed value
 * fails the build rather than producing a broken page at runtime.
 */
const publicEnvSchema = z.object({
  NEXT_PUBLIC_SITE_URL: z.url({ protocol: /^https?$/ }),
  NEXT_PUBLIC_API_BASE_URL: z.url({ protocol: /^https?$/ }),
  /**
   * Cloudflare Turnstile site key for the contact form. Public by design — it
   * identifies the widget to Cloudflare and is readable in any page that
   * renders one. The matching secret key never leaves the backend.
   */
  NEXT_PUBLIC_TURNSTILE_SITE_KEY: z
    .string()
    .min(1, "Required — use a Cloudflare test site key for local development"),
});

/** Strips a trailing slash so callers can always join with `/path`. */
function withoutTrailingSlash(url: string): string {
  return url.replace(/\/+$/, "");
}

function parsePublicEnv() {
  const parsed = publicEnvSchema.safeParse({
    // Referenced statically so Next can inline the values at build time.
    NEXT_PUBLIC_SITE_URL: process.env.NEXT_PUBLIC_SITE_URL,
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
    NEXT_PUBLIC_TURNSTILE_SITE_KEY: process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY,
  });

  if (!parsed.success) {
    const issues = parsed.error.issues
      .map((issue) => `  ${issue.path.join(".")}: ${issue.message}`)
      .join("\n");

    throw new Error(
      `Invalid public environment variables:\n${issues}\n\n` +
        "Copy frontend/.env.example to frontend/.env.local and fill in the values.",
    );
  }

  return {
    siteUrl: withoutTrailingSlash(parsed.data.NEXT_PUBLIC_SITE_URL),
    apiBaseUrl: withoutTrailingSlash(parsed.data.NEXT_PUBLIC_API_BASE_URL),
    turnstileSiteKey: parsed.data.NEXT_PUBLIC_TURNSTILE_SITE_KEY,
  };
}

export const env = parsePublicEnv();

export type Env = typeof env;
