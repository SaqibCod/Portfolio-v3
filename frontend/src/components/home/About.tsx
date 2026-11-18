import { AboutObjects } from "@/components/home/FloatingObjects";
import { Badge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";

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
    <Section id="about" className="py-27 text-center md:py-37">
      <AboutObjects />
      <Container className="relative">
        <p className="eyebrow">01 — Who I am</p>
        <h2 className="display metal mt-4.5 text-[10vw] md:text-[clamp(44px,7vw,112px)]">
          About me
        </h2>
        <p className="mx-auto mt-9 max-w-[760px] text-lg leading-snug font-medium md:text-[22px]">
          I’m a software engineer who builds products end to end — from{" "}
          <b className="font-extrabold text-fg">Spring Boot</b> services and{" "}
          <b className="font-extrabold text-fg">RAG pipelines</b> to fast,
          accessible <b className="font-extrabold text-fg">Next.js</b>{" "}
          front-ends. Lately:{" "}
          <b className="font-extrabold text-fg">githMind</b>, a
          chat-with-your-repo tool, and{" "}
          <b className="font-extrabold text-fg">Medi-Scan</b>, an AI
          lab-report analyzer.
        </p>

        <ul
          aria-label="Quick facts"
          className="mt-8 flex list-none flex-wrap justify-center gap-2.5"
        >
          <li>
            <Badge>[CONFIRM: X] years building software</Badge>
          </li>
          <li>
            <Badge>Based in [CONFIRM: City]</Badge>
          </li>
          <li>
            <Badge dot="ok">Open to [CONFIRM: role type]</Badge>
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
