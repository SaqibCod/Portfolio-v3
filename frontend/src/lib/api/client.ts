import createClient from "openapi-fetch";

import { env } from "@/lib/env";

import type { paths } from "@/lib/api/schema";

/**
 * The only way the app talks to the Spring Boot API.
 *
 * Typed from `schema.d.ts`, which is generated from
 * `docs/api/portfolio-api.yaml` by `pnpm gen:api`. A wrong path, method,
 * parameter or response field is a `pnpm typecheck` failure, not a runtime
 * surprise — so never hand-write API types.
 */
export const apiClient = createClient<paths>({
  baseUrl: env.apiBaseUrl,
  headers: { Accept: "application/json" },
});

export type ApiClient = typeof apiClient;
