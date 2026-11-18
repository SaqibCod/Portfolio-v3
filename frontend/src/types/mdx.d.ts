/**
 * `@types/mdx` types only the default export of an `*.mdx` module. Case studies
 * also export their `meta` object, which it cannot infer, so we declare it
 * here — as `unknown`, which forces every reader through the zod schema in
 * `src/lib/projects.ts`.
 *
 * This file must stay a script (no top-level `import`/`export`) for the
 * declaration to merge with the one `@types/mdx` ships.
 */
declare module "*.mdx" {
  export const meta: unknown;
}
