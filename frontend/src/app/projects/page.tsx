import { ProjectGrid } from "@/components/projects/ProjectGrid";
import { JsonLd } from "@/components/seo/JsonLd";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { PendingSlot } from "@/components/ui/PendingSlot";
import { Section } from "@/components/ui/Section";
import { breadcrumbJsonLd } from "@/lib/jsonld";
import { getAllProjects } from "@/lib/projects";
import { buildMetadata } from "@/lib/seo";

import type { Metadata } from "next";

const description =
  "Things I have designed, built and shipped — from AI tools to the backend behind this site. Every project has a full case study.";

export const metadata: Metadata = buildMetadata({
  title: "Projects",
  description,
  path: "/projects",
});

/**
 * `.phero` + `.list` — every project, newest first.
 *
 * The export also draws a category filter row. Filtering is client state that
 * would either hide server-rendered content from crawlers or push the page off
 * static rendering, so with three projects the whole list is simply shown.
 */
export default function ProjectsPage() {
  const projects = getAllProjects();

  return (
    <>
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Home", path: "/" },
          { name: "Projects", path: "/projects" },
        ])}
      />

      <Section spacing="none" className="pt-14 pb-6 md:pt-24 md:pb-10">
        <Container className="relative">
          <p className="eyebrow">Selected work · {projects.length} projects</p>
          <h1 className="display metal mt-4.5 text-[10vw] md:text-[clamp(48px,9.2vw,132px)]">
            Projects
            <span
              className="ml-3 align-top font-mono text-[clamp(20px,2.4vw,32px)] text-ac1 [-webkit-text-fill-color:var(--ac1)]"
              aria-hidden="true"
            >
              ({projects.length})
            </span>
          </h1>

          <div className="mt-8 flex flex-col items-start gap-5 md:flex-row md:items-end md:justify-between md:gap-8">
            <p className="max-w-[520px] text-[17px] leading-relaxed text-mute md:text-[19px]">
              {description}
            </p>
            <PendingSlot
              label="Live status"
              className="w-full md:max-w-[320px]"
            >
              Each card will carry its uptime and response time from my
              monitor once the status page lands.
            </PendingSlot>
          </div>
        </Container>
      </Section>

      <Section spacing="none" className="pt-9 pb-22 md:pt-14 md:pb-30">
        <Container>
          <ProjectGrid projects={projects} label="All projects" />
        </Container>
      </Section>

      <Section spacing="none" className="pb-35 md:pb-50">
        <Container>
          <div className="flex flex-col items-start justify-between gap-8 rounded-xl bg-fg px-6 py-10 text-bg md:flex-row md:items-center md:rounded-footer md:px-14 md:py-18">
            <div>
              <h2 className="display text-[clamp(40px,5vw,72px)]">
                Curious how they work?
              </h2>
              <p className="mt-3.5 max-w-[460px] leading-relaxed opacity-75">
                Every project has a case study covering the problem, the
                architecture and the decisions behind it.
              </p>
            </div>
            <div className="flex w-full flex-wrap gap-3 md:w-auto">
              <ButtonLink
                href="/tech"
                className="border-bg text-bg hover:border-bg hover:text-bg max-md:w-full"
              >
                How this site works
              </ButtonLink>
              <ButtonLink
                href="/#contact"
                className="border-bg text-bg hover:border-bg hover:text-bg max-md:w-full"
              >
                Get in touch
              </ButtonLink>
            </div>
          </div>
        </Container>
      </Section>
    </>
  );
}
