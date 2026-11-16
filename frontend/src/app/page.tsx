import { Badge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { buildMetadata } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

import type { Metadata } from "next";

export const metadata: Metadata = buildMetadata({
  title: "Home",
  description: siteConfig.description,
  path: "/",
});

export default function Home() {
  return (
    <>
      <Section className="text-center">
        <Container>
          <p className="eyebrow">Phase 02 — Frontend core</p>
          <h1 className="display metal mt-4 text-[clamp(44px,9.6vw,112px)]">
            Hi, I’m {siteConfig.name}
          </h1>
          <p className="mx-auto mt-9 max-w-[760px] text-lg leading-relaxed font-medium md:text-[22px]">
            {siteConfig.tagline}
          </p>
          {/* The export runs hero CTAs full-width below the breakpoint. */}
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <ButtonLink
              href="/#contact"
              variant="primary"
              className="max-md:w-full"
            >
              Contact Me
            </ButtonLink>
            <ButtonLink href="/projects" className="max-md:w-full">
              See Projects
            </ButtonLink>
          </div>
        </Container>
      </Section>

      <Section inverted rounded>
        <Container>
          <p className="eyebrow">Shell</p>
          <h2 className="display mt-4 text-[clamp(32px,7vw,64px)]">
            Tokens, Theme &amp; Primitives
          </h2>
          <p className="mt-6 max-w-[560px] leading-relaxed text-mute">
            The header, footer, navigation, theme toggle and UI primitives are
            in place. Real page content lands in phase 03.
          </p>
          <div className="mt-8 grid gap-6 md:grid-cols-2">
            <Card>
              <h3 className="display text-xl">Both Themes</h3>
              <p className="mt-4 leading-relaxed text-mute">
                Every surface, border and text tier reads from a CSS variable,
                so the toggle in the header repaints the whole page.
              </p>
              <div className="mt-5 flex flex-wrap gap-2">
                <Badge dot="ok">Operational</Badge>
                <Badge dot="warn">Degraded</Badge>
                <Badge dot="off">Unknown</Badge>
              </div>
            </Card>
            <Card>
              <h3 className="display text-xl">Typed API Client</h3>
              <p className="mt-4 leading-relaxed text-mute">
                <code className="font-mono text-sm">openapi-fetch</code> is
                typed straight from the OpenAPI contract, so a wrong path or
                field fails the typecheck.
              </p>
              <div className="mt-5 flex flex-wrap gap-2">
                <Badge>Next.js</Badge>
                <Badge>Spring Boot</Badge>
                <Badge>PostgreSQL</Badge>
              </div>
            </Card>
          </div>
        </Container>
      </Section>
    </>
  );
}
