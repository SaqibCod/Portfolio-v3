import { ImageResponse } from "next/og";

import {
  OgCard,
  loadOgFonts,
  ogContentType,
  ogSize,
} from "@/components/seo/OgCard";
import { getProject, getProjectSlugs } from "@/lib/projects";
import { siteConfig } from "@/lib/site-config";

export const alt = "Case study";
export const size = ogSize;
export const contentType = ogContentType;

/**
 * A metadata route needs its own params to be prerendered — the page's copy
 * does not reach it — so without this the cards would be rendered on demand.
 */
export function generateStaticParams() {
  return getProjectSlugs().map((slug) => ({ slug }));
}

export const dynamicParams = false;

/**
 * One card per case study, so a shared project link previews with that
 * project's name rather than the site's.
 *
 * `params` is a promise in Next 16. The page's `generateStaticParams` and
 * `dynamicParams = false` govern this route too, so every card is generated
 * at build time.
 */
export default async function Image({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const project = getProject(slug);

  return new ImageResponse(
    (
      <OgCard
        eyebrow={project ? `Case study · ${project.kind}` : "Case study"}
        title={project?.title ?? siteConfig.fullName}
        subtitle={project?.summary ?? siteConfig.headline}
        footer={`${siteConfig.siteUrl.replace(/^https?:\/\//, "")}/projects/${slug}`}
      />
    ),
    { ...size, fonts: await loadOgFonts() },
  );
}
