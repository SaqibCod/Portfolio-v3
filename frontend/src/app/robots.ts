import { absoluteUrl } from "@/lib/seo";

import type { MetadataRoute } from "next";

/**
 * Everything public is crawlable. `/admin` is the owner's moderation area —
 * it is behind a token, but keeping it out of the index means its URL never
 * shows up in search results in the first place.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: "/admin",
    },
    sitemap: absoluteUrl("/sitemap.xml"),
    host: absoluteUrl("/"),
  };
}
