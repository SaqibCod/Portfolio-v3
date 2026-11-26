import { cn } from "@/lib/cn";

type KbdProps = React.ComponentPropsWithoutRef<"kbd">;

/**
 * The export's `kbd`: an 11px mono cap with a 2px bottom border, which is the
 * whole trick that makes it read as a physical key.
 *
 * Shared by the header hint, the palette footer and the terminal's hint bar, so
 * every key the site names looks like the same keyboard.
 */
export function Kbd({ className, ...props }: KbdProps) {
  return (
    <kbd
      className={cn(
        "inline-flex items-center rounded-kbd border border-b-2 border-line " +
          "bg-bg2 px-1.5 py-1 font-mono text-[11px] leading-none text-mute",
        className,
      )}
      {...props}
    />
  );
}
