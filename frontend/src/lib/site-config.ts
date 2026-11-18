import { env } from "@/lib/env";

export type NavItem = {
  href: string;
  label: string;
};

export type SocialLink = {
  href: string;
  label: string;
};

/**
 * Single source of truth for the site's identity and navigation.
 *
 * Placeholder values match the Claude Design exports, which use bracketed
 * stand-ins. The real name, email, location and handles are confirmed in
 * phase 03 (see docs/plan/README.md, "Decisions to confirm").
 */
export const siteConfig = {
  name: "Saq",
  /** Wordmark: the trailing dot is accented in the design. */
  wordmark: "SAQ",
  role: "Full-stack engineer",
  tagline:
    "Full-stack engineer building AI-powered products with Spring Boot, Next.js and LLMs.",
  description:
    "Full-stack engineer building AI-powered products with Spring Boot, Next.js and LLMs. Case studies, live GitHub activity and an AI that answers questions about my work.",
  email: "hello@example.com",
  location: "[City, Country]",
  siteUrl: env.siteUrl,
  locale: "en_US",

  /** The hero portrait, also the `Person` JSON-LD image. */
  avatar: {
    src: "/avatar-smile-clear.png",
    alt: "3D avatar of Saq, smiling",
  },

  /** `Person.knowsAbout` — the subjects the site is actually about. */
  knowsAbout: [
    "Java",
    "Spring Boot",
    "Spring AI",
    "Next.js",
    "React",
    "TypeScript",
    "PostgreSQL",
    "Retrieval-augmented generation",
  ],

  nav: [
    { href: "/#about", label: "About" },
    { href: "/projects", label: "Projects" },
    { href: "/status", label: "Status" },
    { href: "/guestbook", label: "Guestbook" },
    { href: "/#contact", label: "Contact" },
  ] satisfies NavItem[],

  social: [
    { href: "https://github.com/", label: "GitHub" },
    { href: "https://linkedin.com/", label: "LinkedIn" },
    { href: "https://x.com/", label: "X / Twitter" },
  ] satisfies SocialLink[],

  /** Footer "Site" column. */
  footerSite: [
    { href: "/projects", label: "Projects" },
    { href: "/status", label: "Status" },
    { href: "/guestbook", label: "Guestbook" },
    { href: "/tech", label: "Tech behind this site" },
  ] satisfies NavItem[],

  builtWith: "Built with Next.js + Spring Boot",
} as const;

export type SiteConfig = typeof siteConfig;
