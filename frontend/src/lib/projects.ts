import { z } from "zod";

import * as githmind from "@/content/projects/githmind.mdx";
import * as mediScan from "@/content/projects/medi-scan.mdx";
import * as portfolio from "@/content/projects/portfolio.mdx";

import type { MDXProps } from "mdx/types";

/**
 * Front-matter contract for a case study. Every `src/content/projects/*.mdx`
 * exports a `meta` object shaped like this; the parse below runs at module
 * load, so an invalid or missing field fails `pnpm build` rather than
 * rendering a half-empty page.
 */
const projectMetaSchema = z.object({
  /** Project name, as shown in the `h1` and the card heading. */
  title: z.string().min(1),
  /** The mono eyebrow above the name, e.g. "AI · Full-stack". */
  kind: z.string().min(1),
  /** One sentence; also the page description and OG description. */
  summary: z.string().min(1).max(240),
  /** First shipped (YYYY-MM-DD). Orders the list, newest first. */
  date: z.iso.date(),
  /** Last substantive change (YYYY-MM-DD). Feeds `sitemap.lastModified`. */
  updated: z.iso.date(),
  /** Coarse categories, for grouping and JSON-LD keywords. */
  tags: z.array(z.string().min(1)).min(1),
  /** Technologies, rendered as chips. */
  stack: z.array(z.string().min(1)).min(1),
  /** Shown in the home page's "Selected work" band. */
  featured: z.boolean(),
  /** What you did on it, e.g. "Solo — design, backend, frontend". */
  role: z.string().min(1),
  /** Human-readable span, e.g. "Mar 2026 – Sep 2026". */
  timeline: z.string().min(1),
  /** Deployed app. Omitted while a project has nowhere to point. */
  demoUrl: z.url().optional(),
  /** Source repository. */
  repoUrl: z.url().optional(),
  /** Cover image under `public/`. Without one, pages show a labelled slot. */
  cover: z
    .object({ src: z.string().startsWith("/"), alt: z.string().min(1) })
    .optional(),
});

export type ProjectMeta = z.infer<typeof projectMetaSchema>;

export type Project = ProjectMeta & {
  slug: string;
  /** Route of the case study. */
  href: string;
  /** Zero-padded position in the list, as the design numbers its cards. */
  number: string;
  /** The compiled MDX body. */
  Content: (props: MDXProps) => React.JSX.Element;
};

/**
 * Case studies all follow the same template, so the on-this-page nav is a
 * constant rather than something parsed out of the MDX. `projects.test.ts`
 * asserts every `.mdx` file still carries all of these headings.
 */
export const caseStudySections = [
  { id: "overview", label: "Overview" },
  { id: "the-problem", label: "The problem" },
  { id: "architecture", label: "Architecture" },
  { id: "key-decisions", label: "Key decisions" },
  { id: "demo", label: "Demo" },
  { id: "challenges-results", label: "Challenges & results" },
] as const;

/** Raw modules, keyed by the slug each one is served at. */
const modules: Record<string, unknown> = {
  githmind,
  "medi-scan": mediScan,
  portfolio,
};

type ProjectModule = {
  default: (props: MDXProps) => React.JSX.Element;
  meta: unknown;
};

function toProject(slug: string, mod: unknown): Omit<Project, "number"> {
  const { default: Content, meta } = mod as ProjectModule;
  const parsed = projectMetaSchema.safeParse(meta);

  if (!parsed.success) {
    const issues = parsed.error.issues
      .map((issue) => `  ${issue.path.join(".") || "(root)"}: ${issue.message}`)
      .join("\n");

    throw new Error(
      `Invalid meta in src/content/projects/${slug}.mdx:\n${issues}`,
    );
  }

  return {
    ...parsed.data,
    slug,
    href: `/projects/${slug}`,
    Content,
  };
}

/** Newest first, which is the order every list and the numbering follow. */
const projects: Project[] = Object.entries(modules)
  .map(([slug, mod]) => toProject(slug, mod))
  .sort((a, b) => b.date.localeCompare(a.date))
  .map((project, index) => ({
    ...project,
    number: String(index + 1).padStart(2, "0"),
  }));

export function getAllProjects(): Project[] {
  return projects;
}

export function getFeaturedProjects(): Project[] {
  return projects.filter((project) => project.featured);
}

export function getProjectSlugs(): string[] {
  return projects.map((project) => project.slug);
}

export function getProject(slug: string): Project | undefined {
  return projects.find((project) => project.slug === slug);
}

/**
 * The previous and next case study in list order, for the footer pager. Ends
 * of the list simply have one side missing — the pager never wraps around.
 */
export function getProjectNeighbours(slug: string): {
  previous?: Project;
  next?: Project;
} {
  const index = projects.findIndex((project) => project.slug === slug);
  if (index === -1) return {};
  return { previous: projects[index - 1], next: projects[index + 1] };
}
