import Link from "next/link";

import { FooterShapes } from "@/components/layout/FooterShapes";
import { PresenceChip } from "@/components/presence/PresenceChip";
import { StatusChip } from "@/components/status/StatusChip";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { siteConfig } from "@/lib/site-config";

type FooterColumnProps = {
  title: string;
  children: React.ReactNode;
};

/**
 * A labelled link group. The label is a paragraph rather than a heading so the
 * footer doesn't inject extra levels into the page's heading outline; the list
 * points at it with `aria-labelledby` instead.
 */
function FooterColumn({ title, children }: FooterColumnProps) {
  const labelId = `footer-${title.toLowerCase()}`;

  return (
    <div>
      <p
        id={labelId}
        className="mb-4 font-mono text-chip font-medium tracking-[0.1em] uppercase text-mute"
      >
        {title}
      </p>
      <ul aria-labelledby={labelId} className="flex flex-col gap-2.5">
        {children}
      </ul>
    </div>
  );
}

const linkClasses = "text-[15px] font-semibold no-underline";

/**
 * The footer card: a hairline-bordered panel at 40px radius that lifts over
 * the last band, carrying the stroked wordmark, three link columns, the
 * copyright line and the decorative shape row.
 */
export function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="relative z-3 -mt-10 px-2.5 pb-2.5 md:-mt-15 md:px-6 md:pb-6">
      <div className="mx-auto box-border max-w-[1392px] rounded-xl border border-line bg-bg px-5.5 pt-9 pb-7 md:rounded-footer md:px-14 md:pt-14 md:pb-10">
        <nav
          aria-label="Footer"
          className="grid grid-cols-2 gap-x-5 gap-y-8 md:grid-cols-[minmax(0,1.4fr)_repeat(3,minmax(0,1fr))] md:gap-10"
        >
          <p className="display col-span-full m-0 text-[30vw] leading-[0.85] text-transparent [-webkit-text-stroke:2px_var(--fg)] md:col-span-1 md:text-[clamp(72px,10vw,150px)]">
            {siteConfig.name}
          </p>

          <FooterColumn title="Social">
            {siteConfig.social.map((item) => (
              <li key={item.href}>
                <a
                  href={item.href}
                  rel="me noreferrer"
                  target="_blank"
                  className={linkClasses}
                >
                  {item.label}
                </a>
              </li>
            ))}
          </FooterColumn>

          <FooterColumn title="Site">
            {siteConfig.footerSite.map((item) => (
              <li key={item.href}>
                <Link href={item.href} className={linkClasses}>
                  {item.label}
                </Link>
              </li>
            ))}
          </FooterColumn>

          <FooterColumn title="Contact">
            <li>
              <a href={`mailto:${siteConfig.email}`} className={linkClasses}>
                {siteConfig.email}
              </a>
            </li>
            <li>
              <span className="text-[15px] text-mute">
                {siteConfig.location}
              </span>
            </li>
          </FooterColumn>
        </nav>

        <div className="mt-12 flex flex-wrap items-center justify-between gap-4 border-t border-line pt-6">
          {/* Status and presence render nothing until phases 07 and 09 wire
              their data; the theme chip is always there. */}
          <div className="flex flex-wrap gap-3">
            <StatusChip />
            <PresenceChip wording="long" live={false} />
            <ThemeToggle variant="chip" />
          </div>
          <span className="font-mono text-chip text-mute">
            © {year} {siteConfig.name} · {siteConfig.builtWith}
          </span>
        </div>

        <FooterShapes />
      </div>
    </footer>
  );
}
