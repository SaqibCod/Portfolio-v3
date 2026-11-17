import type { Metadata } from "next";

import { siteConfig } from "@/lib/site-config";

export type BuildMetadataOptions = {
  /** Page title, without the site suffix — the root template adds it. */
  title: string;
  description: string;
  /** Route path, e.g. "/projects". Used for the canonical and OG URLs. */
  path: string;
  /** Absolute or site-relative OG image. Defaults to the site OG image. */
  image?: string;
  /** Set for pages that must stay out of search results. */
  noIndex?: boolean;
};

/** Normalises a path to a single leading slash and no trailing slash. */
export function normalizePath(path: string): string {
  if (!path || path === "/") return "/";
  const withLeadingSlash = path.startsWith("/") ? path : `/${path}`;
  return withLeadingSlash.replace(/\/+$/, "") || "/";
}

/** Resolves a path or absolute URL against the configured site origin. */
export function absoluteUrl(pathOrUrl: string): string {
  if (/^https?:\/\//.test(pathOrUrl)) return pathOrUrl;
  const path = normalizePath(pathOrUrl);
  return path === "/" ? siteConfig.siteUrl : `${siteConfig.siteUrl}${path}`;
}

/**
 * Next serves this path from `app/opengraph-image.tsx`, which phase 03 adds
 * along with the rest of the OG work. Until then the URL resolves to a 404.
 */
export const defaultOgImage = "/opengraph-image";

/**
 * Builds the per-route `Metadata` every page exports: canonical URL, Open
 * Graph and Twitter cards. Values not set here are inherited from the root
 * layout (title template, metadataBase, robots, icons).
 */
export function buildMetadata({
  title,
  description,
  path,
  image = defaultOgImage,
  noIndex = false,
}: BuildMetadataOptions): Metadata {
  const canonicalPath = normalizePath(path);
  const url = absoluteUrl(canonicalPath);
  const imageUrl = absoluteUrl(image);

  return {
    title,
    description,
    alternates: { canonical: canonicalPath },
    openGraph: {
      type: "website",
      siteName: siteConfig.name,
      locale: siteConfig.locale,
      title,
      description,
      url,
      images: [{ url: imageUrl, alt: title }],
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: [imageUrl],
    },
    ...(noIndex ? { robots: { index: false, follow: false } } : {}),
  };
}
