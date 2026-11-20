import { cn } from "@/lib/cn";

type SpinnerProps = {
  className?: string;
};

/**
 * `.spin` from the exports: the 16px three-quarter arc that sits inside a
 * button while a request is in flight.
 *
 * Decorative — the button's own label changes to "Sending…" or "Loading…"
 * alongside it, so the state is already readable without the icon.
 */
export function Spinner({ className }: SpinnerProps) {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.6"
      strokeLinecap="round"
      aria-hidden="true"
      className={cn("motion-safe:animate-spin", className)}
    >
      <path d="M21 12a9 9 0 1 1-6.2-8.6" />
    </svg>
  );
}
