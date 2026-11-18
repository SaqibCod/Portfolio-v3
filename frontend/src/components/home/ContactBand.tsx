import { ContactObjects } from "@/components/home/FloatingObjects";
import { Container } from "@/components/ui/Container";
import { PendingSlot } from "@/components/ui/PendingSlot";
import { Section } from "@/components/ui/Section";
import { siteConfig } from "@/lib/site-config";

/**
 * `.contact` — section 07, on the inverted band.
 *
 * The address and the invitation are static HTML, so the page is reachable
 * even with the API down; the form itself is a later phase and is marked as a
 * slot rather than rendered as a dead input.
 */
export function ContactBand() {
  return (
    <Section
      id="contact"
      inverted
      rounded
      className="overflow-hidden pt-20 pb-37 md:pt-30 md:pb-50"
    >
      <ContactObjects />
      <Container className="relative grid gap-10 md:grid-cols-2 md:items-start md:gap-16">
        <div>
          <p className="eyebrow">07 — Contact</p>
          <h2 className="display mt-4.5 text-[clamp(48px,6.4vw,96px)]">
            Let’s get in touch
          </h2>
          <a
            href={`mailto:${siteConfig.email}`}
            className="mt-7 inline-block text-[22px] font-semibold underline underline-offset-6"
          >
            {siteConfig.email}
          </a>
          <p className="mt-4 max-w-[380px] text-[15px] leading-relaxed text-mute">
            Got a role, a project or a question? Send a message and I’ll reply
            by email.
          </p>
        </div>

        <PendingSlot label="Contact form">
          The form that posts to <code className="font-mono">/api/v1/contact</code>{" "}
          — with its bot check, per-field validation and rate limits — arrives
          with the contact phase. Until then, email is the way through.
        </PendingSlot>
      </Container>
    </Section>
  );
}
