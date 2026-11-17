import { cn } from "@/lib/cn";

type CardProps = React.ComponentPropsWithoutRef<"div">;

/**
 * The workhorse surface: elevated ground, 1px hairline, 24px radius, 28px of
 * padding (20px below the breakpoint) — `.card` in the exports.
 */
export function Card({ className, ...props }: CardProps) {
  return (
    <div
      className={cn(
        "min-w-0 rounded-lg border border-line bg-bg2 p-5 md:rounded-card md:p-7",
        className,
      )}
      {...props}
    />
  );
}
