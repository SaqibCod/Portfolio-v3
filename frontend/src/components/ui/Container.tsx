import { cn } from "@/lib/cn";

type ContainerProps = React.ComponentPropsWithoutRef<"div">;

/**
 * The design's `.wrap`: a 1280px column with 80px gutters that tighten to 20px
 * below the 760px breakpoint. Every band's content sits inside one.
 */
export function Container({ className, ...props }: ContainerProps) {
  return (
    <div
      className={cn("mx-auto w-full max-w-wrap px-5 md:px-20", className)}
      {...props}
    />
  );
}
