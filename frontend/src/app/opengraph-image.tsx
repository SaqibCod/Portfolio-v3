import { ImageResponse } from "next/og";

import {
  OgCard,
  loadOgFonts,
  ogContentType,
  ogSize,
} from "@/components/seo/OgCard";
import { siteConfig } from "@/lib/site-config";

export const alt = `${siteConfig.fullName} — ${siteConfig.role}`;
export const size = ogSize;
export const contentType = ogContentType;

/** The card every route falls back to, unless it ships its own. */
export default async function Image() {
  return new ImageResponse(
    (
      <OgCard
        eyebrow={siteConfig.role}
        title={siteConfig.fullName}
        subtitle={siteConfig.headline}
        footer={siteConfig.siteUrl.replace(/^https?:\/\//, "")}
      />
    ),
    { ...size, fonts: await loadOgFonts() },
  );
}
