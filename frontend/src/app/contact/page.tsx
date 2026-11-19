import { ContactForm } from "@/components/contact/ContactForm";
import { ContactObjects } from "@/components/home/FloatingObjects";
import { JsonLd } from "@/components/seo/JsonLd";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { breadcrumbJsonLd } from "@/lib/jsonld";
import { buildMetadata } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

import type { Metadata } from "next";

const description =
  "Send me a message about a role, a project or a question, and I’ll reply by email.";

export const metadata: Metadata = buildMetadata({
  title: "Contact",
  description,
  path: "/contact",
});

/**
 * The contact form on its own URL.
 *
 * The design puts this form in section 07 of the home page, and every link in
 * the site still points there — this page exists so the form also has an
 * address of its own to link to from outside, and it reuses the band's layout
 * exactly rather than inventing a second one. The same `ContactForm` component
 * serves both.
 */
export default function ContactPage() {
  return (
    <>
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Home", path: "/" },
          { name: "Contact", path: "/contact" },
        ])}
      />

      <Section
        inverted
        className="overflow-hidden pt-20 pb-37 md:pt-30 md:pb-50"
      >
        <ContactObjects />
        <Container className="relative grid gap-10 md:grid-cols-2 md:items-start md:gap-16">
          <div>
            <p className="eyebrow">Contact</p>
            <h1 className="display mt-4.5 text-[clamp(48px,6.4vw,96px)]">
              Let’s get in touch
            </h1>
            <a
              href={`mailto:${siteConfig.email}`}
              className="mt-7 inline-block text-[22px] font-semibold underline underline-offset-6"
            >
              {siteConfig.email}
            </a>
            <p className="mt-4 max-w-[380px] text-[15px] leading-relaxed text-mute">
              Got a role, a project or a question? Send a message and I’ll
              reply by email. {siteConfig.location}.
            </p>
          </div>

          <ContactForm successHeadingLevel="h2" />
        </Container>
      </Section>
    </>
  );
}
