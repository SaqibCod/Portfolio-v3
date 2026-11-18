import path from "node:path";
import mdx from "@mdx-js/rollup";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    // `next build` compiles MDX through @next/mdx; Vitest needs its own
    // loader to import the same case-study files. `pre` puts it ahead of the
    // React plugin, which only understands the JSX that MDX compiles down to.
    { ...mdx(), enforce: "pre" },
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
    },
  },
});
