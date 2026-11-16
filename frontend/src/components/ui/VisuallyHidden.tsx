import { cn } from "@/lib/cn";

type VisuallyHiddenProps = React.ComponentPropsWithoutRef<"span">;

/** Keeps text in the accessibility tree while hiding it visually. */
export function VisuallyHidden({ className, ...props }: VisuallyHiddenProps) {
  return <span className={cn("sr-only", className)} {...props} />;
}
