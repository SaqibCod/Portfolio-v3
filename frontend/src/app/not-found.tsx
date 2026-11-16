import Link from "next/link";

import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";

/**
 * `not-found.tsx` renders inside the root layout, so it inherits the shell and
 * the root metadata. Next only reads `metadata` exports from layout, page and
 * route files — a 404-specific title needs `app/global-not-found.tsx`, which
 * is still experimental, so it is deliberately not set here.
 */

/** The "Popular pages" grid from the error export. */
const popularPages = [
  { href: "/", title: "Home", description: "Start from the top." },
  {
    href: "/projects",
    title: "Projects",
    description: "Everything I’ve built.",
  },
  { href: "/guestbook", title: "Guestbook", description: "Leave a note." },
  { href: "/status", title: "Status", description: "Is everything up?" },
];

function ArrowIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

/** The drifting objects behind the error code. Decorative only. */
function Orbs() {
  return (
    <div aria-hidden="true">
      <span className="absolute top-10 left-[2%] size-14 rounded-full bg-[image:var(--orb-sphere)] motion-safe:animate-[bob_7s_ease-in-out_infinite] md:top-30 md:left-[8%] md:size-[110px]" />
      <span className="absolute top-7.5 right-[2%] h-7.5 w-17.5 -rotate-24 rounded-full bg-[image:var(--orb-capsule)] motion-safe:animate-[bobr_8s_ease-in-out_infinite] md:top-22.5 md:right-[9%] md:h-14.5 md:w-35" />
      {/* Below the breakpoint this drops to the lower left, clear of the
          card grid, as the export's mobile rule does. */}
      <span className="absolute bottom-10 left-[4%] bg-[image:var(--orb-glyph)] bg-clip-text font-mono text-[52px] leading-none font-semibold text-transparent motion-safe:animate-[bob_6s_ease-in-out_infinite] md:right-[14%] md:bottom-65 md:left-auto md:text-[84px]">
        ?
      </span>
      <span className="absolute bottom-75 left-[12%] hidden size-22.5 box-border rounded-full border-20 border-transparent bg-[image:var(--orb-ring)] bg-origin-border [mask:radial-gradient(farthest-side,transparent_calc(100%-20px),#000_calc(100%-19px))] motion-safe:animate-[bob_9s_ease-in-out_infinite] md:block" />
    </div>
  );
}

export default function NotFound() {
  return (
    <div className="relative pt-12 pb-40 text-center md:pt-18 md:pb-55">
      <Orbs />
      <Container className="relative">
        <p className="eyebrow">Error 404</p>
        <h1 className="display metal mt-6 text-[26vw] leading-none md:text-[clamp(110px,16vw,230px)]">
          404
        </h1>
        <h2 className="display mt-7 text-[8vw] md:mt-9 md:text-[clamp(30px,4.2vw,60px)]">
          This page wandered off
        </h2>
        <p className="mx-auto mt-5 max-w-[560px] text-base leading-relaxed text-mute md:text-lg">
          The link might be broken, or the page moved. Even I can’t find it —
          and I built this place.
        </p>

        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <ButtonLink href="/" variant="primary" className="max-md:w-full">
            Back to Home
          </ButtonLink>
          <ButtonLink href="/projects" className="max-md:w-full">
            See All Projects
          </ButtonLink>
        </div>

        <nav
          aria-label="Popular pages"
          className="mx-auto mt-11 grid max-w-[960px] grid-cols-2 gap-3 text-left md:mt-16 md:grid-cols-4"
        >
          {popularPages.map((page) => (
            <Link
              key={page.href}
              href={page.href}
              className="flex flex-col gap-2 rounded-lg border border-line bg-bg2 p-4 no-underline transition-[border-color,transform] duration-200 hover:-translate-y-[3px] hover:border-fg hover:text-fg md:p-4.5"
            >
              <b className="display flex items-center justify-between text-[15px] md:text-lg">
                {page.title}
                <ArrowIcon />
              </b>
              <span className="text-sm leading-normal text-mute">
                {page.description}
              </span>
            </Link>
          ))}
        </nav>
      </Container>
    </div>
  );
}
