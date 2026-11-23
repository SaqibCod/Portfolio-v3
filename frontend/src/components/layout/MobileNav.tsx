"use client";

import Link from "next/link";

import type { NavItem } from "@/lib/site-config";

type MobileNavProps = {
  id: string;
  /** The same list the desktop row shows, so the two never disagree. */
  items: readonly NavItem[];
  onNavigate: () => void;
};

/**
 * The menu the nav row collapses into below 760px: a stacked list of 32px
 * display-weight links, each separated by a hairline — `.mmenu` in the
 * exports.
 */
export function MobileNav({ id, items, onNavigate }: MobileNavProps) {
  return (
    <nav
      id={id}
      aria-label="Mobile"
      className="sticky top-nav-sm z-19 flex flex-col border-b border-line bg-bg px-5 pt-2 pb-6 md:hidden"
    >
      {items.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          onClick={onNavigate}
          className="display border-b border-line py-2.5 text-[32px] no-underline"
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
