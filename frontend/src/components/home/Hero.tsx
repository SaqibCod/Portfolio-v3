import Image from "next/image";

import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { siteConfig } from "@/lib/site-config";

/**
 * `.hero` — the first screen: the greeting, the portrait, and the one-line
 * pitch beside the primary call to action.
 *
 * The portrait is the page's LCP element, so it is `priority` with explicit
 * dimensions. The export's poke-and-swap avatar toy is not built here: this
 * phase ships static pages, and a caption promising interaction that does not
 * exist would be worse than no caption.
 */
export function Hero() {
  return (
    <section id="top" className="relative pt-9 pb-12 text-center md:pt-14 md:pb-16">
      <Container>
        {/* The space between the spans is a text node of its own, so the
            accessible name reads "Hi, I’m Saq" — a trailing space inside the
            first span would be trimmed away. Below the breakpoint each span
            becomes a block and the space collapses, as in the export. */}
        <h1 className="display metal relative z-1 text-[14vw] md:text-[clamp(44px,9.6vw,150px)] [&>span]:max-md:block">
          <span>Hi, I’m</span>{" "}
          <span>{siteConfig.name}</span>
        </h1>

        <div className="relative z-2 mx-auto mt-[-6vw] size-70 md:mt-[-5.5vw] md:size-105">
          <Image
            src={siteConfig.avatar.src}
            alt={siteConfig.avatar.alt}
            width={420}
            height={420}
            priority
            sizes="(min-width: 47.5rem) 420px, 280px"
            className="size-full rounded-full object-cover shadow-[0_0_0_1px_var(--line),0_40px_90px_-30px_var(--ring)] motion-safe:animate-[float_5.5s_ease-in-out_infinite]"
          />
          <p className="absolute bottom-[-22px] left-1/2 flex -translate-x-1/2 items-center gap-2 rounded-full border border-line bg-bg2 px-3.5 py-2.5 font-mono text-eyebrow whitespace-nowrap shadow-lift md:bottom-14.5 md:left-auto md:translate-x-0 md:-right-9">
            <i
              aria-hidden="true"
              className="size-2 shrink-0 rounded-full bg-ok"
            />
            Shipping features
          </p>
        </div>

        <div className="mt-7 flex flex-col items-stretch gap-6 text-left md:mt-10 md:flex-row md:items-end md:justify-between md:gap-8">
          <p className="max-w-[360px] font-mono text-eyebrow leading-relaxed tracking-[0.04em] uppercase text-mute">
            {siteConfig.tagline}
          </p>
          <ButtonLink href="/#contact" variant="primary" className="max-md:w-full">
            Contact me
          </ButtonLink>
        </div>
      </Container>
    </section>
  );
}
