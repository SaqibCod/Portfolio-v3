import { cn } from "@/lib/cn";

/**
 * Shared field chrome from `.fld input` / `.fld textarea`: elevated ground,
 * hairline border, 14px radius. Focus replaces the global outline with the
 * design's accent border plus a 3px violet ring.
 */
export const fieldClasses =
  "w-full min-w-0 rounded-input border border-line bg-bg2 px-4 py-3.5 " +
  "text-base font-medium leading-[1.45] text-fg " +
  "placeholder:text-mute " +
  "transition-[border-color,box-shadow] duration-150 " +
  "focus-visible:border-ac1 focus-visible:shadow-[0_0_0_3px_var(--ring)] focus-visible:outline-none " +
  "aria-invalid:border-err disabled:cursor-not-allowed disabled:opacity-60";

type InputProps = React.ComponentPropsWithoutRef<"input">;

export function Input({ className, ...props }: InputProps) {
  return <input className={cn(fieldClasses, "min-h-12", className)} {...props} />;
}
