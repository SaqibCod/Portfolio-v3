import { ProjectCard } from "@/components/projects/ProjectCard";
import { cn } from "@/lib/cn";

import type { Project } from "@/lib/projects";

type ProjectGridProps = {
  projects: Project[];
  /** Names the list for assistive tech, e.g. "All projects". */
  label: string;
  variant?: "featured" | "list";
  /** Heading level for each card; see `ProjectCard`. */
  cardAs?: "h2" | "h3";
  className?: string;
};

/**
 * The stacked column of project cards. A real `ul` so the count is announced,
 * and the covers alternate sides down the list as `.card.rev` does.
 *
 * Only the first card loads its cover eagerly — everything below it is well
 * past the fold and must not compete with the page's LCP.
 */
export function ProjectGrid({
  projects,
  label,
  variant = "list",
  cardAs = "h2",
  className,
}: ProjectGridProps) {
  if (projects.length === 0) {
    return (
      <div className="flex flex-col items-center gap-4 rounded-2xl border border-line border-dashed px-6 py-16 text-center">
        <h3 className="display text-[28px] [font-stretch:120%]">
          Nothing here yet
        </h3>
        <p className="text-mute">The first case study is on its way.</p>
      </div>
    );
  }

  return (
    <ul
      aria-label={label}
      className={cn(
        "flex list-none flex-col",
        variant === "featured" ? "gap-8" : "gap-5 md:gap-8",
        className,
      )}
    >
      {projects.map((project, index) => (
        <li key={project.slug} className="min-w-0">
          <ProjectCard
            project={project}
            variant={variant}
            as={cardAs}
            priority={index === 0}
            reversed={variant === "list" && index % 2 === 1}
          />
        </li>
      ))}
    </ul>
  );
}
