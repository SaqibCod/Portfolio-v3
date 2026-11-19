import path from "node:path";
import mdx from "@mdx-js/rollup";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    // `next build` compiles MDX through @next/mdx; Vitest needs its own
    // loader to import the same case-study files. `pre` puts it ahead of the
    // React plugin, which only understands the JSX that MDX compiles down to.
    //
    // `providerImportSource` points at the same `useMDXComponents` the App
    // Router uses, so a case study resolves `<MetricTiles>` and friends in a
    // test exactly as it does in a build.
    {
      ...mdx({ providerImportSource: "@/mdx-components" }),
      enforce: "pre",
    },
    react(),
  ],
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "src") },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    // src/lib/env.ts validates these at import time, so the suite needs them.
    env: {
      NEXT_PUBLIC_SITE_URL: "https://example.com",
      NEXT_PUBLIC_API_BASE_URL: "https://api.example.com",
      // Cloudflare's documented "always passes" test site key.
      NEXT_PUBLIC_TURNSTILE_SITE_KEY: "1x00000000000000000000AA",
    },
  },
});
