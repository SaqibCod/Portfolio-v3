"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { cn } from "@/lib/cn";
import type { NavItem } from "@/lib/site-config";

/**
 * True when `href` points at the page we're on.
 *
 * Section anchors (`/#about`, `/#contact`) are never "current": they all live
 * on the home page, so treating them as current would underline several links
 * at once and claim `aria-current="page"` for a fragment.
 */
function isCurrent(href: string, pathname: string): boolean {
  if (href.includes("#")) return false;
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

type NavLinksProps = {
  /** The links to show. `/status` is absent unless a project is monitored. */
  items: readonly NavItem[];
  className?: string;
  /** Called after a link is activated, so the mobile menu can close itself. */
  onNavigate?: () => void;
};

/**
 * The desktop nav row: 13px uppercase JetBrains Mono in `mute`, rising to `fg`
 * on hover. The current page is underlined in the violet accent, as in
 * `.links a.cur`.
 */
export function NavLinks({ items, className, onNavigate }: NavLinksProps) {
  const pathname = usePathname();

  return (
    <ul className={cn("flex items-center gap-8", className)}>
      {items.map((item) => {
        const current = isCurrent(item.href, pathname);
        return (
          <li key={item.href}>
            <Link
              href={item.href}
              onClick={onNavigate}
              aria-current={current ? "page" : undefined}
              className={cn(
                "font-mono text-eyebrow tracking-[0.08em] uppercase no-underline",
                "transition-colors duration-150 hover:text-fg",
                current
                  ? "text-fg underline decoration-ac1 decoration-2 underline-offset-8"
                  : "text-mute",
              )}
            >
              {item.label}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
