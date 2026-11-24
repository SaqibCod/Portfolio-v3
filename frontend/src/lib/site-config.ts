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
    "Saqib Choudhary — MCA student at IIT Patna building end-to-end systems with Java, Spring Boot, Spring AI and Next.js. Open to internships.",
  email: "saqibc097@gmail.com",
  location: "Delhi, India",
  siteUrl: env.siteUrl,
  locale: "en_US",

  /** The hero portrait, also the `Person` JSON-LD image. */
  avatar: {
    src: "/avatar-smile-clear.png",
    alt: "3D avatar of Saqib Choudhary, smiling",
  },

  /** `Person.affiliation` — where the degree is being taken. */
  university: "Indian Institute of Technology Patna",

  /**
   * `Person.knowsAbout` — the subjects the site is actually about.
   *
   * Drawn from the skills the knowledge base lists, and deliberately shorter
   * than that list: this is structured data a search engine reads as a claim
   * about expertise, so it names what the projects on this site demonstrate
   * rather than everything that has ever been installed.
   */
  knowsAbout: [
    "Java",
    "Spring Boot",
    "Spring AI",
    "Next.js",
    "React",
    "TypeScript",
    "Python",
    "PostgreSQL",
    "pgvector",
    "Retrieval-augmented generation",
    "Large language model integration",
    "REST API design",
    "Docker",
    "CI/CD",
  ],

  nav: [
    { href: "/#about", label: "About" },
    { href: "/projects", label: "Projects" },
    { href: "/status", label: "Status" },
    { href: "/guestbook", label: "Guestbook" },
    { href: "/#contact", label: "Contact" },
  ] satisfies NavItem[],

  /**
   * These are also `Person.sameAs`, which is how a search engine ties the
   * profiles to the person — so each one has to be a canonical profile URL.
   * Tracking query strings are stripped, and a network with no profile yet is
   * left out rather than pointed at its homepage.
   */
  social: [
    { href: "https://github.com/SaqibCod", label: "GitHub" },
    {
      href: "https://www.linkedin.com/in/saqib-chaudhary09/",
      label: "LinkedIn",
    },
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
