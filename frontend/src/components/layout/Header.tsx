"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

import { MobileNav } from "@/components/layout/MobileNav";
import { NavLinks } from "@/components/layout/NavLinks";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { Button } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { siteConfig } from "@/lib/site-config";

const MOBILE_NAV_ID = "mobile-nav";

function MenuIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}

/**
 * The sticky translucent nav: wordmark, link row, theme toggle, and a menu
 * trigger that replaces the row below 760px. `--nav-bg` is a semi-transparent
 * ground behind a 14px blur, so the page shows through as it scrolls.
 */
export function Header() {
  const pathname = usePathname();

  // The menu is keyed to the route it opened on, so a navigation — including
  // browser back and forward — collapses it during render rather than in an
  // effect.
  const [menu, setMenu] = useState({ open: false, path: pathname });
  const menuOpen = menu.open && menu.path === pathname;

  const setMenuOpen = (open: boolean) => setMenu({ open, path: pathname });
  const closeMenu = () => setMenuOpen(false);

  return (
    <>
      <header className="sticky top-0 z-20 border-b border-line bg-nav backdrop-blur-[14px]">
        <Container className="flex h-nav-sm items-center justify-between gap-6 md:h-nav">
          <Link
            href="/"
            className="display text-[22px] no-underline hover:text-fg"
          >
            {siteConfig.wordmark}
            <span className="text-ac1">.</span>
          </Link>

          <NavLinks className="hidden md:flex" />

          <div className="flex items-center gap-3">
            <ThemeToggle />
            <Button
              size="icon"
              className="md:hidden"
              onClick={() => setMenuOpen(!menuOpen)}
              aria-label={menuOpen ? "Close menu" : "Open menu"}
              aria-expanded={menuOpen}
              aria-controls={MOBILE_NAV_ID}
            >
              {menuOpen ? <CloseIcon /> : <MenuIcon />}
            </Button>
          </div>
        </Container>
      </header>

      {menuOpen ? (
        <MobileNav id={MOBILE_NAV_ID} onNavigate={closeMenu} />
      ) : null}
    </>
  );
}
