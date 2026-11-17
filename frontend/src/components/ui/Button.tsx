import Link from "next/link";

import { cn } from "@/lib/cn";

export type ButtonVariant = "primary" | "secondary";
export type ButtonSize = "default" | "icon";

/**
 * `.btn` from the exports: a 999px pill, 44px tall, uppercase Archivo 600 at
 * 0.04em. `primary` carries the violet→fuchsia brand gradient; `secondary` is
 * the hairline outline that brightens to `fg` on hover.
 */
const base =
  "inline-flex cursor-pointer touch-manipulation items-center justify-center gap-2 " +
  "rounded-full border text-btn font-semibold leading-none tracking-[0.04em] uppercase " +
  "no-underline transition-colors duration-150";

const sizes: Record<ButtonSize, string> = {
  default: "min-h-11 px-[22px]",
  icon: "size-11 shrink-0 p-0",
};

const variants: Record<ButtonVariant, string> = {
  primary: "grad border-transparent text-white hover:brightness-110",
  secondary:
    "border-line bg-transparent text-fg hover:border-fg hover:text-fg",
};

function buttonClasses(
  variant: ButtonVariant,
  size: ButtonSize,
  className?: string,
) {
  return cn(
    base,
    sizes[size],
    variants[variant],
    "disabled:cursor-not-allowed disabled:opacity-60",
    className,
  );
}

type ButtonProps = React.ComponentPropsWithoutRef<"button"> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
};

export function Button({
  variant = "secondary",
  size = "default",
  className,
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClasses(variant, size, className)}
      {...props}
    />
  );
}

type ButtonLinkProps = React.ComponentPropsWithoutRef<typeof Link> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
};

/** The same pill as a real link, so Cmd/middle-click still open a new tab. */
export function ButtonLink({
  variant = "secondary",
  size = "default",
  className,
  ...props
}: ButtonLinkProps) {
  return (
    <Link className={buttonClasses(variant, size, className)} {...props} />
  );
}
