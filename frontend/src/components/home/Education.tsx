import { Badge } from "@/components/ui/Badge";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { cn } from "@/lib/cn";

type Entry = {
  years: string;
  status: string;
  /** `ok` marks the entry still in progress, as `.ed.now` does. */
  current?: boolean;
  qualification: string;
  institution: string;
  place: string;
  facts: string[];
  coursework?: string[];
  note?: string;
};

/**
 * The timeline draws itself from this list, newest first.
 *
 * No CGPA on the MCA entry: it is a first-year result that does not exist yet,
 * and an empty badge reads better than a bracketed one. Coursework is left out
 * for the same reason — listing module names nobody has taken yet would be the
 * one thing on this page that is not true.
 */
const entries: Entry[] = [
  {
    years: "2026 – 2028",
    status: "In progress",
    current: true,
    qualification: "Master of Computer Applications (MCA)",
    institution: "Indian Institute of Technology Patna",
    place: "Patna, India",
    facts: ["First year"],
    note: "Focused on AI engineering and end-to-end systems — the projects on this site are where that happens.",
  },
  {
    years: "[CONFIRM: YYYY – YYYY]",
    status: "Completed",
    qualification: "Bachelor of Computer Applications (BCA)",
    institution: "Institute of Management and Education (IME)",
    place: "Ghaziabad, India",
    facts: ["7.5 CGPA"],
  },
];

function Label({ children }: { children: React.ReactNode }) {
  return (
    <p className="font-mono text-[11px] tracking-[0.08em] uppercase text-mute">
      {children}
    </p>
  );
}

/**
 * `.edu` — section 05. The timeline rail is drawn with a gradient on the
 * list's `::before` and a dot per entry, so the shape comes from the layout
 * rather than from decorative elements in the markup.
 *
 * The export's "Download CV" button and its certifications row are both left
 * out: there is no CV file to link and no certification to list, and a dashed
 * placeholder card advertises an absence rather than filling it.
 */
export function Education() {
  return (
    <Section id="education" spacing="none" className="pb-25 md:pb-37">
      <Container>
        <p className="eyebrow">05 — Education</p>
        <h2 className="display metal mt-4.5 text-[10vw] md:text-[clamp(44px,7vw,112px)]">
          Education
        </h2>

        <ol className="relative mt-8 flex list-none flex-col gap-5 pl-7 before:absolute before:top-2 before:bottom-2 before:left-[5px] before:w-0.5 before:bg-[linear-gradient(180deg,var(--ac1),var(--ac2)_60%,var(--line))] md:mt-12 md:pl-11 md:before:left-2.75">
          {entries.map((entry) => (
            <li key={entry.qualification} className="relative">
              <span
                aria-hidden="true"
                className={cn(
                  "absolute top-6.5 -left-7 box-border size-3.5 rounded-full border-3 border-ac1 md:top-8 md:-left-11 md:size-4",
                  entry.current ? "bg-ac1 shadow-[0_0_0_6px_var(--ring)]" : "bg-bg",
                )}
              />
              <article className="grid gap-3.5 rounded-lg border border-line bg-bg2 p-5 md:grid-cols-[200px_minmax(0,1fr)] md:gap-7 md:rounded-card md:p-7">
                <div className="flex flex-row flex-wrap items-center gap-2.5 md:flex-col md:items-start">
                  <b className="display text-2xl leading-none md:text-3xl">
                    {entry.years}
                  </b>
                  <Badge dot={entry.current ? "ok" : undefined}>
                    {entry.status}
                  </Badge>
                </div>
                <div className="min-w-0">
                  <h3 className="display text-xl leading-tight [font-stretch:118%] md:text-2xl">
                    {entry.qualification}
                  </h3>
                  <p className="mt-2 text-base font-semibold">
                    {entry.institution}{" "}
                    <span className="font-medium text-mute">
                      · {entry.place}
                    </span>
                  </p>
                  <ul
                    aria-label={`${entry.qualification} highlights`}
                    className="mt-4 flex list-none flex-wrap gap-2"
                  >
                    {entry.facts.map((fact) => (
                      <li key={fact}>
                        <Badge>{fact}</Badge>
                      </li>
                    ))}
                  </ul>
                  {entry.coursework ? (
                    <>
                      <Label>Relevant coursework</Label>
                      <ul
                        aria-label="Relevant coursework"
                        className="mt-2.5 flex list-none flex-wrap gap-2"
                      >
                        {entry.coursework.map((course) => (
                          <li key={course}>
                            <Badge>{course}</Badge>
                          </li>
                        ))}
                      </ul>
                    </>
                  ) : null}
                  {entry.note ? (
                    <p className="mt-4 text-[15px] leading-relaxed text-mute">
                      {entry.note}
                    </p>
                  ) : null}
                </div>
              </article>
            </li>
          ))}
        </ol>
      </Container>
    </Section>
  );
}
