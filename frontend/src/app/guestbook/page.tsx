import { GuestbookSection } from "@/components/guestbook/GuestbookSection";
import { GuestbookObjects } from "@/components/guestbook/GuestbookObjects";
import { JsonLd } from "@/components/seo/JsonLd";
import { Badge } from "@/components/ui/Badge";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { breadcrumbJsonLd } from "@/lib/jsonld";
import { buildMetadata } from "@/lib/seo";
import { fetchApprovedEntries } from "@/lib/guestbook";

import type { Metadata } from "next";

const description =
  "Say hi, share feedback, or tell me what brought you here. No account needed — every note is read and approved before it appears.";

export const metadata: Metadata = buildMetadata({
  title: "Guestbook",
  description,
  path: "/guestbook",
});

/**
 * How often this page may be rebuilt, in seconds.
 *
 * Belt and braces with the `next: { revalidate }` on the fetch itself: the
 * segment value is what makes the *page* static with a one-minute lifetime,
 * and the fetch value is what keeps its data from outliving that.
 *
 * Written as a literal, not as the `GUESTBOOK_REVALIDATE_SECONDS` the fetch
 * uses: Next requires this export to be statically analyzable, so an imported
 * constant (or an expression) is not allowed here. Keep the two in step.
 */
export const revalidate = 60;

/**
 * `.head` + `.main` from the guestbook export.
 *
 * The first page of approved notes is fetched here, on the server, so the notes
 * are real HTML in the response rather than something that appears after a
 * round trip — the whole point of a page a search engine should be able to
 * read. Everything after that first page is the list's own business.
 *
 * An unreachable API is not an error page: the head, the form and an inline
 * notice still render, because a backend outage must not take out a route.
 */
export default async function GuestbookPage() {
  const page = await fetchApprovedEntries();

  return (
    <>
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Home", path: "/" },
          { name: "Guestbook", path: "/guestbook" },
        ])}
      />

      <Section spacing="none" className="overflow-hidden pt-14 md:pt-22">
        <GuestbookObjects />
        <Container className="relative">
          <p className="eyebrow">Leave a note</p>
          <h1 className="display metal mt-4.5 text-[clamp(44px,8.6vw,128px)]">
            Guestbook
          </h1>
          <div className="mt-7 flex flex-wrap items-end justify-between gap-8">
            <p className="m-0 max-w-[560px] text-[17px] leading-relaxed text-mute md:text-[19px]">
              Say hi, share feedback, or tell me what brought you here. No
              account needed — I read every note and approve it before it shows
              up.
            </p>
            <Badge>Held for approval</Badge>
          </div>
        </Container>
      </Section>

      <Container className="pt-9 md:pt-14">
        <GuestbookSection
          initialPage={page ?? { items: [], nextCursor: null }}
          unavailable={page === null}
        />
      </Container>
    </>
  );
}
