"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";

/**
 * Exactly two themes, dark and light, selected by a class on `<html>` so the
 * token blocks in `globals.css` and Tailwind's `dark:` variant agree.
 *
 * next-themes injects a blocking inline script that sets the class before
 * first paint, so there is no flash of the wrong theme. The first visit
 * follows the system setting; after that the choice is remembered.
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      themes={["light", "dark"]}
      disableTransitionOnChange
    >
      {children}
    </NextThemesProvider>
  );
}
