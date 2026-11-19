import { getAllProjects } from "@/lib/projects";
import { absoluteUrl } from "@/lib/seo";

import type { MetadataRoute } from "next";

/**
 * Hand-maintained `lastModified` for the pages whose content lives in TSX
 * rather than in a content file. Bump it when you meaningfully change one.
 */
const pageUpdated = {
  home: "2026-11-18",
  projects: "2026-11-18",
  tech: "2026-11-18",
  contact: "2026-11-19",
} as const;

/**
 * Every route the site actually serves. Pages that later phases add —
 * /status, /guestbook — are deliberately absent: listing a URL that 404s is
 * worse than omitting it.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const projects = getAllProjects();

  return [
    {
      url: absoluteUrl("/"),
      lastModified: pageUpdated.home,
      changeFrequency: "weekly",
      priority: 1,
    },
    {
      url: absoluteUrl("/projects"),
      lastModified: pageUpdated.projects,
      changeFrequency: "weekly",
      priority: 0.8,
    },
    {
      url: absoluteUrl("/contact"),
      lastModified: pageUpdated.contact,
      changeFrequency: "monthly",
      priority: 0.7,
    },
    {
      url: absoluteUrl("/tech"),
      lastModified: pageUpdated.tech,
      changeFrequency: "monthly",
      priority: 0.6,
    },
    ...projects.map((project) => ({
      url: absoluteUrl(project.href),
      // Straight from the case study's own front matter, so publishing an
      // edit is all it takes to update the sitemap.
      lastModified: project.updated,
      changeFrequency: "monthly" as const,
      priority: 0.7,
    })),
  ];
}
