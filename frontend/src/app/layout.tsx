import { Archivo, JetBrains_Mono } from "next/font/google";

import { ChatLauncher } from "@/components/chat/ChatLauncher";
import { Footer } from "@/components/layout/Footer";
import { Header } from "@/components/layout/Header";
import { SkipLink } from "@/components/layout/SkipLink";
import { CommandLauncher } from "@/components/palette/CommandLauncher";
import { PresenceProvider } from "@/components/presence/PresenceProvider";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { commandPages, commandProjects } from "@/lib/commands/registry";
import { footerSiteNav, primaryNav } from "@/lib/navigation";
import { getAllProjects } from "@/lib/projects";
import { siteConfig } from "@/lib/site-config";
import { statusPageIsPublished } from "@/lib/status";

import "./globals.css";

import type { Metadata, Viewport } from "next";

/**
 * Archivo carries everything. The design leans on its width axis (100% for
 * body, 115–125% for display type), so `wdth` has to be requested explicitly —
 * `next/font/google` ships only the weight axis by default.
 */
const archivo = Archivo({
  subsets: ["latin"],
  axes: ["wdth"],
  display: "swap",
  variable: "--font-archivo",
});

/** Eyebrows, chips, metadata and code. */
const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-jetbrains-mono",
});

export const metadata: Metadata = {
  metadataBase: new URL(siteConfig.siteUrl),
  title: {
    default: `${siteConfig.fullName} — ${siteConfig.role}`,
    template: `%s | ${siteConfig.fullName}`,
  },
  description: siteConfig.description,
  applicationName: siteConfig.fullName,
  authors: [{ name: siteConfig.fullName, url: siteConfig.siteUrl }],
  creator: siteConfig.fullName,
  robots: { index: true, follow: true },
  icons: { icon: "/favicon.ico" },
};

export const viewport: Viewport = {
  // Matches --bg in each theme so the browser chrome blends with the page.
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f4f3ef" },
    { media: "(prefers-color-scheme: dark)", color: "#08080a" },
  ],
};

/**
 * Asks the API whether there is a status page to link to, once per hour.
 *
 * This is the one thing the layout fetches, and it costs something worth
 * naming: a fetch here gives every route a revalidation window, so pages that
 * would otherwise be fully static are rebuilt at most hourly. That is the price
 * of the rule that deploying a project needs no change to this app — the link
 * has to follow the backend's `app.status.services`, and only the backend knows
 * what is in it. An hour is chosen to make the price as small as possible:
 * "does the owner monitor anything" changes when a project ships, not when its
 * health does.
 */
export default async function RootLayout({ children }: LayoutProps<"/">) {
  const statusPublished = await statusPageIsPublished();

  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${archivo.variable} ${jetbrainsMono.variable} h-full`}
    >
      <body className="flex min-h-full flex-col overflow-x-clip">
        <ThemeProvider>
          {/* Presence wraps the tree because the header badge and the footer
              badge read one socket between them, and because living in the
              layout is what lets it survive navigation: a route change sends a
              join on the open connection instead of reconnecting. */}
          <PresenceProvider>
            <SkipLink />
            <Header nav={primaryNav(statusPublished)} />
            {/* tabIndex -1 makes the skip link move focus onto <main> itself
                rather than only setting the next tab stop, which is what
                Safari and several screen readers need. */}
            <main
              id="main"
              tabIndex={-1}
              className="flex flex-1 flex-col focus-visible:outline-none"
            >
              {children}
            </main>
            <Footer siteLinks={footerSiteNav(statusPublished)} />
            {/* Outside <main> and after the footer: a floating control is page
                chrome, not content, so it belongs last in the tab order and
                outside the landmark the skip link jumps to. */}
            <ChatLauncher />
            {/* The registry's data is built here because both halves of it are
                server-only: `/status` is a page only while something is
                monitored, and the project summaries come from compiled MDX
                that must never be pulled into a browser chunk. */}
            <CommandLauncher
              data={{
                pages: commandPages(statusPublished),
                projects: commandProjects(getAllProjects()),
              }}
            />
          </PresenceProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
