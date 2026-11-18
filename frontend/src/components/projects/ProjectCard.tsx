import { CoverFrame } from "@/components/projects/CoverFrame";
import { TechStackList } from "@/components/projects/TechStackList";
import { ButtonLink } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

import type { Project } from "@/lib/projects";

type ProjectCardProps = {
  project: Project;
  /**
   * `featured` is the home page's `.pcard`; `list` is the wider two-column
   * `.card` on /projects.
   */
  variant?: "featured" | "list";
  /**
   * The card's own heading level. On /projects each card is a section of the
   * page, so it is an `h2`; inside the home page's "Projects" band it sits
   * under that band's `h2` and must be an `h3`.
   */
  as?: "h2" | "h3";
  /** Only the first card on a page may skip lazy-loading its cover. */
  priority?: boolean;
  /** Mirrors `.card.rev` — the list alternates which side the cover sits on. */
  reversed?: boolean;
};

/** "Year" in the meta row — the year the project first shipped. */
function shippedYear(date: string): string {
  return date.slice(0, 4);
}

type MetaCell = { label: string; value: string };

function MetaRow({ cells }: { cells: MetaCell[] }) {
  return (
    <dl className="grid grid-cols-2 border-t border-b border-line md:grid-cols-3">
      {cells.map((cell, index) => (
        <div
          key={cell.label}
          className={cn(
            "flex min-w-0 flex-col gap-1.5 py-3.5",
            index > 0 && "md:border-l md:border-line md:pl-4",
            // The third cell drops to a full-width row below the breakpoint,
            // where the grid is only two columns wide.
            index === 2 && "col-span-2 border-t border-line md:col-span-1 md:border-t-0",
          )}
        >
          <dt className="font-mono text-[11px] tracking-[0.08em] uppercase text-mute">
            {cell.label}
          </dt>
          <dd className="text-[15px] break-words">{cell.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * One project, as shown on the home page's "Selected work" band and on
 * /projects. The whole card is static: the live status and uptime the export
 * draws here arrive with the status page in a later phase, so the meta row
 * carries only facts the content file already knows.
 */
export function ProjectCard({
  project,
  variant = "list",
  as: Heading = "h2",
  priority = false,
  reversed = false,
}: ProjectCardProps) {
  const featured = variant === "featured";

  return (
    <article
      className={cn(
        "min-w-0 rounded-lg border border-line bg-bg2 p-5",
        featured ? "md:rounded-xl md:p-7" : "md:rounded-2xl md:p-8",
        // An upward shadow, so a card sliding over the pinned one beneath it
        // reads as a stack rather than as a seam.
        featured && "shadow-[0_-20px_50px_-30px_rgb(0_0_0/0.5)]",
      )}
    >
      <div
        className={cn(
          "grid gap-6 md:items-stretch",
          featured
            ? "md:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]"
            : "md:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] md:gap-10",
        )}
      >
        <div className={cn("flex min-w-0 flex-col gap-5", reversed && "md:order-2")}>
          <div className="flex items-start gap-4.5">
            <span
              aria-hidden="true"
              className={cn(
                "display leading-[0.85] tracking-[-0.03em]",
                featured ? "text-4xl md:text-5xl" : "text-5xl md:text-7xl",
              )}
            >
              {project.number}
            </span>
            <div className="min-w-0">
              <p className="eyebrow">{project.kind}</p>
              <Heading
                className={cn(
                  "display mt-1 [font-stretch:120%]",
                  featured ? "text-[22px] md:text-[28px]" : "text-[26px] md:text-4xl",
                )}
              >
                {project.title}
              </Heading>
            </div>
          </div>

          <p className="text-[17px] leading-relaxed">{project.summary}</p>

          <TechStackList
            items={project.stack}
            label={`${project.title} stack`}
          />

          <MetaRow
            cells={[
              { label: "Year", value: shippedYear(project.date) },
              { label: "Role", value: project.role },
              { label: "Timeline", value: project.timeline },
            ]}
          />

          <div className="mt-auto flex flex-wrap gap-2.5">
            <ButtonLink
              href={project.href}
              variant="primary"
              className="max-md:flex-1"
            >
              Case study
            </ButtonLink>
            {project.demoUrl ? (
              <ButtonLink
                href={project.demoUrl}
                target="_blank"
                rel="noreferrer"
                className="max-md:flex-1"
              >
                Live
              </ButtonLink>
            ) : null}
            {project.repoUrl ? (
              <ButtonLink
                href={project.repoUrl}
                target="_blank"
                rel="noreferrer"
                className="max-md:flex-1"
              >
                Code
              </ButtonLink>
            ) : null}
          </div>
        </div>

        <CoverFrame
          cover={project.cover}
          label={`${project.title} screenshot`}
          priority={priority}
          className={cn(
            "max-md:order-first",
            featured ? "min-h-50 md:min-h-75" : "min-h-65 md:min-h-95",
          )}
        />
      </div>
    </article>
  );
}
