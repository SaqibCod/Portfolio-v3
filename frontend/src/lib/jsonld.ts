import { absoluteUrl } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

import type { Project } from "@/lib/projects";

/** A JSON-LD node, as handed to `<JsonLd>`. */
export type JsonLdObject = Record<string, unknown>;

const SCHEMA = "https://schema.org";

/** Stable `@id` for the one Person on the site, so other nodes can refer to it. */
const personId = `${siteConfig.siteUrl}/#person`;

/**
 * `Person` for the home page: who the site is about, and the profiles that
 * corroborate it. `sameAs` is what lets a search engine tie the three together.
 */
export function personJsonLd(): JsonLdObject {
  return {
    "@context": SCHEMA,
    "@type": "Person",
    "@id": personId,
    name: siteConfig.name,
    jobTitle: siteConfig.role,
    description: siteConfig.tagline,
    url: siteConfig.siteUrl,
    image: absoluteUrl(siteConfig.avatar.src),
    email: `mailto:${siteConfig.email}`,
    sameAs: siteConfig.social.map((link) => link.href),
    knowsAbout: siteConfig.knowsAbout,
  };
}

/**
 * A case study is a `CreativeWork` *about* a `SoftwareApplication` — the page
 * is the article, the project is its subject. Describing the page as the
 * application itself would claim an app's properties (price, rating, platform)
 * that a write-up does not have.
 */
export function projectJsonLd(project: Project): JsonLdObject {
  const url = absoluteUrl(project.href);

  const about: JsonLdObject = {
    "@type": "SoftwareApplication",
    name: project.title,
    applicationCategory: "DeveloperApplication",
    description: project.summary,
    ...(project.demoUrl ? { url: project.demoUrl } : {}),
  };

  return {
    "@context": SCHEMA,
    "@type": "CreativeWork",
    "@id": `${url}#case-study`,
    name: `${project.title} — case study`,
    headline: project.title,
    description: project.summary,
    url,
    inLanguage: siteConfig.locale.replace("_", "-"),
    datePublished: project.date,
    dateModified: project.updated,
    keywords: [...project.tags, ...project.stack],
    author: { "@id": personId },
    about,
    ...(project.cover
      ? { image: absoluteUrl(project.cover.src) }
      : { image: absoluteUrl(`${project.href}/opengraph-image`) }),
  };
}

export type BreadcrumbItem = {
  name: string;
  /** Site-relative path, e.g. "/projects". */
  path: string;
};

/**
 * `BreadcrumbList` for a nested page. Every item needs an absolute URL,
 * including the last one, or the trail is dropped as incomplete.
 */
export function breadcrumbJsonLd(items: BreadcrumbItem[]): JsonLdObject {
  return {
    "@context": SCHEMA,
    "@type": "BreadcrumbList",
    itemListElement: items.map((item, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: item.name,
      item: absoluteUrl(item.path),
    })),
  };
}
