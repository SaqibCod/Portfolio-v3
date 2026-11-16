import { cn } from "@/lib/cn";

type SectionProps = React.ComponentPropsWithoutRef<"section"> & {
  /**
   * Flips the band onto the opposite ground, like `.inv` in the exports.
   * Children keep using the same `bg2`/`fg`/`mute`/`line` utilities.
   */
  inverted?: boolean;
  /** Rounds the top corners (44px, 28px on mobile), like `.round`. */
  rounded?: boolean;
};

/**
 * A full-width vertical band. The design's rhythm is 80px of padding on mobile
 * rising to 120px on desktop; pass `className` to override per section.
 */
export function Section({
  inverted = false,
  rounded = false,
  className,
  ...props
}: SectionProps) {
  return (
    <section
      className={cn(
        "relative py-20 md:py-30",
        inverted && "inv-band",
        rounded && "rounded-t-xl md:rounded-t-band",
        className,
      )}
      {...props}
    />
  );
}
