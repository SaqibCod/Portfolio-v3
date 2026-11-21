import { AdminGate } from "@/components/admin/AdminGate";
import { Badge } from "@/components/ui/Badge";
import { buildMetadata } from "@/lib/seo";

import type { Metadata } from "next";

export const metadata: Metadata = buildMetadata({
  title: "Admin",
  description: "Owner-only guestbook moderation.",
  path: "/admin",
  // Not a page for anyone else. `robots.ts` disallows /admin too, and
  // `sitemap.ts` deliberately omits it — belt, braces and no advertisement.
  noIndex: true,
});

/**
 * `/admin` — the owner's guestbook moderation page.
 *
 * Everything below the heading is client-only and behind a token held in
 * memory, so there is nothing here to server-render and nothing to index. That
 * is also why this page carries no structured data and no Open Graph image:
 * there is no audience for them.
 *
 * The admin export draws its own minimal chrome — a slim bar with a "noindex"
 * chip and a Lock button, no nav and no footer. The site's header and footer
 * come from the root layout and stay in place here, so what that bar carries
 * that the site chrome does not (the noindex chip, the Lock control) is
 * rendered as part of the page instead. The 1040px column the export uses is
 * narrower than the site's 1280px `.wrap`, and that is kept.
 */
export default function AdminPage() {
  return (
    <div className="mx-auto w-full max-w-[1040px] px-4 py-12 md:px-10 md:py-16">
      <div className="mb-8 flex items-center justify-between gap-4">
        <p className="eyebrow m-0">Admin</p>
        <Badge>
          <NoIndexIcon />
          noindex
        </Badge>
      </div>

      <AdminGate />
    </div>
  );
}

/** The struck-through eye the export puts beside the "noindex" label. */
function NoIndexIcon() {
  return (
    <svg
      width="12"
      height="12"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
      <path d="M3 3l18 18" />
    </svg>
  );
}
