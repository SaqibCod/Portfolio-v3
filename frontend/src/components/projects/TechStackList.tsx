import { Badge } from "@/components/ui/Badge";
import { cn } from "@/lib/cn";

type TechStackListProps = {
  items: string[];
  /**
   * Names the list for assistive tech, e.g. "githMind stack". A bare row of
   * chips reads as loose words otherwise.
   */
  label: string;
  className?: string;
};

/**
 * `.chips` — the hairline pills that carry a project's stack.
 *
 * A real list, so a screen reader announces how many technologies there are,
 * and `translate="no"` on each item keeps browser auto-translation from
 * turning "Spring Boot" into something else.
 */
export function TechStackList({ items, label, className }: TechStackListProps) {
  return (
    <ul
      aria-label={label}
      className={cn("flex list-none flex-wrap gap-2", className)}
    >
      {items.map((item) => (
        <li key={item}>
          <Badge translate="no">{item}</Badge>
        </li>
      ))}
    </ul>
  );
}
