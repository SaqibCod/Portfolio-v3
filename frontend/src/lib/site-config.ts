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
 * Anything still bracketed is a stand-in from the Claude Design exports and
 * needs a real value (see docs/plan/README.md, "Decisions to confirm").
 */
export const siteConfig = {
  /**
   * The short name the design puts in the hero, the footer wordmark and the
   * copyright line. `fullName` is what every metadata and structured-data
   * field uses instead — a search result should carry the whole name.
   */
  name: "Saq",
  fullName: "Saqib Choudhary",
  /** Wordmark: the trailing dot is accented in the design. */
  wordmark: "SAQ",
  /** `Person.jobTitle`, and the suffix on the home page's `<title>`. */
  role: "MCA student at IIT Patna",
  /** The one line beside the hero's call to action. */
  headline: "I build software end to end, with AI where it adds real value",
  /** The meta description every page falls back to. Kept under 160 characters. */
  description:
    "Saqib Choudhary, MCA student at IIT Patna, building end-to-end software systems with Optimize tech and AI.",
  email: "hello@example.com",
  location: "[City, Country]",
  siteUrl: env.siteUrl,
  locale: "en_US",

  /** The hero portrait, also the `Person` JSON-LD image. */
  avatar: {
    src: "/avatar-smile-clear.png",
    alt: "3D avatar of Saqib Choudhary, smiling",
  },

  /** `Person.affiliation` — where the degree is being taken. */
  university: "Indian Institute of Technology Patna",

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
