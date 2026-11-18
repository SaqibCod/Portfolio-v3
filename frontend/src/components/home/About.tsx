import { AboutObjects } from "@/components/home/FloatingObjects";
import { Badge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { siteConfig } from "@/lib/site-config";

/**
 * `.about` — section 01. The one paragraph that has to land, with the facts
 * row under it.
 *
 * The export also puts an "Ask the AI about me" button here. The chatbot is a
 * later phase, so only the link that works today is rendered rather than a
 * button that would do nothing.
 */
export function About() {
  return (
    <Section
      id="about"
      spacing="none"
      // The export's rhythm here is asymmetric: 110/130 on mobile,
      // 150/170 above the breakpoint.
      className="pt-27.5 pb-32.5 text-center md:pt-37.5 md:pb-42.5"
    >
      <AboutObjects />
      <Container className="relative">
        <p className="eyebrow">01 — Who I am</p>
        <h2 className="display metal mt-4.5 text-[10vw] md:text-[clamp(44px,7vw,112px)]">
          About me
        </h2>
        <p className="mx-auto mt-9 max-w-[760px] text-lg leading-snug font-medium md:text-[22px]">
          I’m {siteConfig.fullName}, a{" "}
          <b className="font-extrabold text-fg">
            first-year MCA student at IIT Patna
          </b>
          . I enjoy building{" "}
          <b className="font-extrabold text-fg">complete systems</b>, from the
          interface people use to the backend, data, and AI that power it. I’m
          learning how software engineering is changing with AI, and I learn
          best by{" "}
          <b className="font-extrabold text-fg">shipping real projects</b>.
        </p>

        <ul
          aria-label="Quick facts"
          className="mt-8 flex list-none flex-wrap justify-center gap-2.5"
        >
          <li>
            <Badge>First-year MCA · IIT Patna</Badge>
          </li>
          <li>
            <Badge>Based in [CONFIRM: City, Country]</Badge>
          </li>
          <li>
            <Badge dot="ok">Open to [CONFIRM: internships / roles]</Badge>
          </li>
        </ul>

        <div className="mt-9 flex flex-wrap justify-center gap-3">
          <ButtonLink href="/tech" variant="primary" className="max-md:w-full">
            How this site works
          </ButtonLink>
          <ButtonLink href="/projects" className="max-md:w-full">
            See the work
          </ButtonLink>
        </div>
      </Container>
    </Section>
  );
}
