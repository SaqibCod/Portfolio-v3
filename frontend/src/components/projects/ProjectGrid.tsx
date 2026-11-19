import { ProjectCard } from "@/components/projects/ProjectCard";
import { cn } from "@/lib/cn";

import type { Project } from "@/lib/projects";

/**
 * Where the first featured card pins, and how much lower each one after it
 * pins. The first offset clears the 72px sticky header with room to spare —
 * both values come straight from the export's `.pcard` tops (96/116/136).
 */
const STACK_TOP = 96;
const STACK_STEP = 20;

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

  const stacking = variant === "featured";

  return (
    <ul
      aria-label={label}
      className={cn(
        "list-none",
        // The stacking column keeps its cards in normal flow and spaces them
        // with margins, so each one can pin independently. Flex `gap` would
        // work too, but margins are what the sticky offsets are measured from.
        stacking ? "block" : "flex flex-col gap-5 md:gap-8",
        className,
      )}
    >
      {projects.map((project, index) => (
        <li
          key={project.slug}
          className={cn(
            "min-w-0",
            // Sticky only from the breakpoint up: a card taller than the
            // viewport would pin its top and push its own footer out of
            // reach, and below the breakpoint these cards are exactly that
            // tall. There they simply scroll.
            stacking && "mb-8 last:mb-0 md:sticky",
          )}
          // Each card pins 20px lower than the one before it, so the stack
          // leaves a visible sliver of every card underneath.
          style={stacking ? { top: `${STACK_TOP + index * STACK_STEP}px` } : undefined}
        >
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
