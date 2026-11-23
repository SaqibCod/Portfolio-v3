import { getAllProjects } from "@/lib/projects";
import { absoluteUrl } from "@/lib/seo";
import { statusPageIsPublished } from "@/lib/status";

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
  guestbook: "2026-11-20",
  status: "2026-11-22",
} as const;

/**
 * Every route the site actually serves.
 *
 * `/status` is here only while the API reports a monitored project, because
 * that is the only time the route exists — it 404s otherwise, and listing a URL
 * that 404s is worse than omitting it. Deploying a project and adding it to the
 * backend's `app.status.services` puts it back without a change here.
 *
 * `/admin` is absent for a different reason and must stay that way: it is the
 * owner's moderation page, it is `noindex`, and robots.txt disallows it.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const projects = getAllProjects();
  const statusPublished = await statusPageIsPublished();

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
      url: absoluteUrl("/guestbook"),
      lastModified: pageUpdated.guestbook,
      // The list changes whenever a note is approved, which is the most
      // frequently changing page on the site.
      changeFrequency: "weekly",
      priority: 0.6,
    },
    {
      url: absoluteUrl("/tech"),
      lastModified: pageUpdated.tech,
      changeFrequency: "monthly",
      priority: 0.6,
    },
    ...(statusPublished
      ? [
          {
            url: absoluteUrl("/status"),
            lastModified: pageUpdated.status,
            // The figures move every few minutes, but the page itself — which
            // projects are listed and what it says about them — does not.
            changeFrequency: "daily" as const,
            priority: 0.5,
          },
        ]
      : []),
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
