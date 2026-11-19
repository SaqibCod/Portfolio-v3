import { About } from "@/components/home/About";
import { ContactBand } from "@/components/home/ContactBand";
import { Education } from "@/components/home/Education";
import { Hero } from "@/components/home/Hero";
import { Services } from "@/components/home/Services";
import { TechStrip } from "@/components/home/TechStrip";
import { ProjectGrid } from "@/components/projects/ProjectGrid";
import { JsonLd } from "@/components/seo/JsonLd";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { PendingSlot } from "@/components/ui/PendingSlot";
import { Section } from "@/components/ui/Section";
import { personJsonLd } from "@/lib/jsonld";
import { getFeaturedProjects } from "@/lib/projects";
import { buildMetadata } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

import type { Metadata } from "next";

export const metadata: Metadata = buildMetadata({
  title: `${siteConfig.fullName} — ${siteConfig.role}`,
  description: siteConfig.description,
  path: "/",
  // The home title already names him; the layout's "%s | Saqib Choudhary"
  // template would otherwise repeat it.
  absoluteTitle: true,
});

export default function Home() {
  const featured = getFeaturedProjects();

  return (
    <>
      <JsonLd data={personJsonLd()} />

      <Hero />
      <TechStrip />
      <About />
      <Services />

      {/* 03 — Selected work */}
      <Section id="projects" rounded className="-mt-7 bg-bg md:-mt-11">
        <Container>
          <div className="mb-10 flex flex-col items-start gap-4 md:mb-12 md:flex-row md:items-end md:justify-between md:gap-6">
            <div>
              <p className="eyebrow">03 — Selected work</p>
              <h2 className="display metal mt-4.5 text-[10vw] md:text-[clamp(44px,7vw,112px)]">
                Projects
              </h2>
            </div>
            <ButtonLink href="/projects" className="max-md:w-full">
              All projects
            </ButtonLink>
          </div>
          <ProjectGrid
            projects={featured}
            label="Featured projects"
            variant="featured"
            cardAs="h3"
          />
        </Container>
      </Section>

      {/* 04 — Currently building */}
      <Section id="building" spacing="none" className="py-10 md:py-20">
        <Container>
          <p className="eyebrow">04 — Live from GitHub</p>
          <h2 className="display metal mt-4.5 text-[10vw] md:text-[clamp(44px,7vw,112px)]">
            Currently building
          </h2>
          <div className="mt-8 grid gap-6 md:mt-12 md:grid-cols-[minmax(0,1fr)_minmax(0,1.25fr)]">
            <PendingSlot label="Recent activity">
              The latest commits, pull requests and releases from my public
              repositories, read from the GitHub API and cached server-side.
              Arrives with the GitHub phase.
            </PendingSlot>
            <PendingSlot label="GitHub stats">
              Contribution graph and top languages for the last year, refreshed
              every few hours. Arrives with the GitHub phase.
            </PendingSlot>
          </div>
        </Container>
      </Section>

      <Education />

      {/* 06 — Guestbook */}
      <Section id="guestbook" spacing="none" className="pb-30 md:pb-42">
        <Container>
          <div className="mb-8 flex flex-col items-start gap-4 md:mb-12 md:flex-row md:items-end md:justify-between md:gap-6">
            <div>
              <p className="eyebrow">06 — Guestbook</p>
              <h2 className="display metal mt-4.5 text-[10vw] md:text-[clamp(44px,7vw,112px)]">
                Say hi
              </h2>
              <p className="mt-4.5 max-w-[460px] leading-relaxed text-mute">
                Notes from people who stopped by. Every entry is read and
                approved by me before it shows up here.
              </p>
            </div>
            <ButtonLink
              href="/guestbook"
              variant="primary"
              className="max-md:w-full"
            >
              Sign the guestbook
            </ButtonLink>
          </div>
          <PendingSlot label="Approved entries">
            The three most recent approved notes, loaded from{" "}
            <code className="font-mono">/api/v1/guestbook</code>. Arrives with
            the guestbook phase, along with the page the button above points at.
          </PendingSlot>
        </Container>
      </Section>

      <ContactBand />
    </>
  );
}
