import Link from "next/link";
import { notFound } from "next/navigation";

import { CaseStudyHeader } from "@/components/projects/CaseStudyHeader";
import { JsonLd } from "@/components/seo/JsonLd";
import { Container } from "@/components/ui/Container";
import { PendingSlot } from "@/components/ui/PendingSlot";
import { breadcrumbJsonLd, projectJsonLd } from "@/lib/jsonld";
import {
  caseStudySections,
  getProject,
  getProjectNeighbours,
  getProjectSlugs,
} from "@/lib/projects";
import { buildMetadata } from "@/lib/seo";

import type { Metadata } from "next";

/** Every case study is prerendered from the content directory. */
export function generateStaticParams() {
  return getProjectSlugs().map((slug) => ({ slug }));
}

/**
 * Case studies are a closed set: a slug that no `.mdx` file backs is a 404 at
 * build time rather than a page rendered on demand.
 */
export const dynamicParams = false;

export async function generateMetadata({
  params,
}: PageProps<"/projects/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const project = getProject(slug);

  if (!project) return {};

  return buildMetadata({
    title: project.title,
    description: project.summary,
    path: project.href,
    // This segment ships `opengraph-image.tsx`, so the card is generated per
    // project and must not be overridden by the site-wide default.
    ownOgImage: true,
  });
}

export default async function CaseStudyPage({
  params,
}: PageProps<"/projects/[slug]">) {
  const { slug } = await params;
  const project = getProject(slug);

  // Unreachable with `dynamicParams = false`, but it is what makes `project`
  // non-optional for everything below.
  if (!project) notFound();

  const { Content } = project;
  const { previous, next } = getProjectNeighbours(slug);

  return (
    <>
      <JsonLd data={projectJsonLd(project)} />
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Home", path: "/" },
          { name: "Projects", path: "/projects" },
          { name: project.title, path: project.href },
        ])}
      />

      <section className="pt-8 pb-8 md:pt-14 md:pb-12">
        <Container>
          <CaseStudyHeader project={project} />
          <PendingSlot label="Live status" className="mt-6">
            Uptime, average response time and 30 days of history for{" "}
            {project.title} arrive with the status page.
          </PendingSlot>
        </Container>
      </section>

      <Container className="grid gap-6 pt-14 pb-20 md:grid-cols-[220px_minmax(0,1fr)] md:gap-18 md:pt-24 md:pb-30">
        {/*
          Two shapes. Below the breakpoint the export turns this into a row of
          pills that sticks under the nav and scrolls sideways, bleeding past
          the container's gutters; above it, the familiar rail in the margin.
        */}
        <nav
          aria-label="On this page"
          // `min-w-0` lets the grid column shrink below the pill row's own
          // width; without it the row sizes the column and the page scrolls
          // sideways instead of the pills scrolling inside it.
          className="min-w-0 max-md:sticky max-md:top-nav-sm max-md:z-5 max-md:-mx-5 max-md:border-b max-md:border-line max-md:bg-bg max-md:px-5 max-md:py-3 md:sticky md:top-26 md:self-start"
        >
          <p className="eyebrow mb-3 max-md:hidden">On this page</p>
          <ul className="flex list-none gap-2 overflow-x-auto md:flex-col md:gap-1 md:overflow-x-visible">
            {caseStudySections.map((section) => (
              <li key={section.id} className="max-md:shrink-0">
                <Link
                  href={`${project.href}#${section.id}`}
                  className="flex min-h-10 items-center whitespace-nowrap text-mute no-underline transition-colors duration-150 hover:text-fg max-md:rounded-full max-md:border max-md:border-line max-md:px-3.5 max-md:text-[13px] md:border-l-2 md:border-line md:pl-3.5 md:text-sm md:font-semibold md:hover:border-l-ac1"
                >
                  {section.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        {/* `case-body` numbers each section heading with a CSS counter. */}
        <article className="case-body min-w-0 md:max-w-[760px]">
          <Content />
        </article>
      </Container>

      <Container className="pb-35 md:pb-50">
        <nav
          aria-label="More projects"
          className="flex flex-col gap-5 md:flex-row md:justify-between"
        >
          {previous ? (
            <Link
              href={previous.href}
              className="flex flex-1 flex-col gap-3 rounded-xl border border-line bg-bg2 p-6 no-underline transition-colors duration-200 hover:border-fg hover:text-fg md:p-8"
            >
              <span className="eyebrow">← Previous · {previous.number}</span>
              <b className="display text-[clamp(28px,3.4vw,48px)] leading-tight">
                {previous.title}
              </b>
            </Link>
          ) : null}
          {next ? (
            <Link
              href={next.href}
              className="flex flex-1 flex-col items-end gap-3 rounded-xl border border-line bg-bg2 p-6 text-right no-underline transition-colors duration-200 hover:border-fg hover:text-fg md:p-8"
            >
              <span className="eyebrow">Next · {next.number} →</span>
              <b className="display text-[clamp(28px,3.4vw,48px)] leading-tight">
                {next.title}
              </b>
            </Link>
          ) : null}
        </nav>
      </Container>
    </>
  );
}
