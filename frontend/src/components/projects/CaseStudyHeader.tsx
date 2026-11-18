import Link from "next/link";

import { CoverFrame } from "@/components/projects/CoverFrame";
import { ButtonLink } from "@/components/ui/Button";
import { cn } from "@/lib/cn";
import { formatDate } from "@/lib/format";

import type { Project } from "@/lib/projects";

type CaseStudyHeaderProps = {
  project: Project;
};

type MetaCell = { label: string; value: React.ReactNode };

function MetaGrid({ cells }: { cells: MetaCell[] }) {
  return (
    <dl className="mt-8 grid grid-cols-2 border-t border-b border-line md:mt-12 md:grid-cols-4">
      {cells.map((cell, index) => (
        <div
          key={cell.label}
          className={cn(
            "flex min-w-0 flex-col gap-2 py-5",
            // Two columns below the breakpoint: the right-hand cell of each
            // row is divided from the left, and the second row from the first.
            index % 2 === 1 && "border-l border-line pl-4",
            index >= 2 && "border-t border-line",
            // One row above it, divided between every pair.
            index > 0 ? "md:border-l md:border-line md:pl-6" : "md:border-l-0 md:pl-0",
            "md:border-t-0",
          )}
        >
          <dt className="font-mono text-[11px] tracking-[0.08em] uppercase text-mute">
            {cell.label}
          </dt>
          <dd className="text-base leading-snug font-semibold break-words">
            {cell.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * `.head` — the top of a case study: breadcrumb, title, one-line summary, the
 * facts row, the links out, and the cover.
 *
 * The breadcrumb is a real `nav`/`ol` rather than styled text, so it is both
 * the visible trail and the structure the `BreadcrumbList` JSON-LD describes.
 */
export function CaseStudyHeader({ project }: CaseStudyHeaderProps) {
  return (
    <header>
      <nav aria-label="Breadcrumb">
        <ol className="flex list-none flex-wrap items-center gap-2.5 font-mono text-eyebrow text-mute">
          <li>
            <Link href="/projects" className="no-underline hover:text-fg">
              Projects
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li aria-current="page" className="text-fg">
            {project.title}
          </li>
        </ol>
      </nav>

      {/* Below the breakpoint the export stacks these in reverse, so the
          outlined number sits above the title rather than beside it. */}
      <div className="mt-7 flex flex-col-reverse items-start gap-2 md:mt-10 md:flex-row md:justify-between md:gap-6">
        <h1 className="display metal text-[10.5vw] md:text-[clamp(44px,8.4vw,130px)]">
          {project.title}
        </h1>
        <span
          aria-hidden="true"
          className="display shrink-0 text-[clamp(40px,5vw,72px)] text-transparent [-webkit-text-stroke:2px_var(--fg)]"
        >
          {project.number}
        </span>
      </div>

      <p className="mt-7 max-w-[760px] text-[18px] leading-snug font-medium md:text-[clamp(20px,2vw,26px)]">
        {project.summary}
      </p>

      <MetaGrid
        cells={[
          { label: "Role", value: project.role },
          { label: "Timeline", value: project.timeline },
          { label: "Stack", value: project.stack.join(" · ") },
          {
            label: "Updated",
            value: (
              <time dateTime={project.updated}>
                {formatDate(project.updated)}
              </time>
            ),
          },
        ]}
      />

      {/* The export lets these share rows on narrow screens rather than
          stacking full-width, so a lone link never spans the viewport. */}
      <div className="mt-8 flex flex-wrap items-center gap-3">
        {project.demoUrl ? (
          <ButtonLink
            href={project.demoUrl}
            variant="primary"
            target="_blank"
            rel="noreferrer"
            className="max-md:flex-[1_1_40%]"
          >
            Open live app
          </ButtonLink>
        ) : null}
        {project.repoUrl ? (
          <ButtonLink
            href={project.repoUrl}
            target="_blank"
            rel="noreferrer"
            className="max-md:flex-[1_1_40%]"
          >
            View code
          </ButtonLink>
        ) : null}
        <ButtonLink href="/projects" className="max-md:flex-[1_1_40%]">
          All projects
        </ButtonLink>
      </div>

      <CoverFrame
        cover={project.cover}
        label={`${project.title} hero screenshot · 16:9`}
        chrome={project.demoUrl ?? undefined}
        priority
        className="mt-8 min-h-60 md:mt-12 md:min-h-140"
      />
    </header>
  );
}
