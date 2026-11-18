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
 * The MCA entry is real; everything still bracketed needs a value. Add or
 * remove entries freely — the timeline draws itself from this list.
 */
const entries: Entry[] = [
  {
    years: "[2026 – 2028]",
    status: "In progress · first year",
    current: true,
    qualification: "Master of Computer Applications (MCA)",
    institution: "Indian Institute of Technology Patna",
    place: "Patna, India",
    facts: ["[CONFIRM: CGPA]"],
    coursework: ["[CONFIRM: list your first-year courses]"],
    note: "[Optional: a project, a paper or a highlight — one or two lines.]",
  },
  {
    years: "[YYYY – YYYY]",
    status: "Completed",
    qualification: "[CONFIRM: bachelor’s degree, e.g. BCA or B.Sc Computer Science]",
    institution: "[University name]",
    place: "[City, Country]",
    facts: ["[CONFIRM: CGPA or percentage]"],
  },
];

/** [CONFIRM] Replace or remove. */
const certifications = [
  { name: "[Certification name]", issuer: "[Issuer]", year: "[Year]" },
  { name: "[Certification name]", issuer: "[Issuer]", year: "[Year]" },
  { name: "[Certification name]", issuer: "[Issuer]", year: "[Year]" },
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
 * The export's "Download CV" button is left out until there is a file to
 * download; a link to a missing PDF is worse than no link.
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

        <div className="mt-10">
          <Label>Certifications</Label>
          <ul
            aria-label="Certifications"
            className="mt-3.5 grid list-none gap-3.5 md:grid-cols-3"
          >
            {certifications.map((cert, index) => (
              <li
                key={`${cert.name}-${index}`}
                className="flex items-start gap-3.5 rounded-lg border border-line border-dashed p-4.5"
              >
                <span
                  aria-hidden="true"
                  className="grad flex size-10 shrink-0 items-center justify-center rounded-md text-white"
                >
                  <svg
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <circle cx="12" cy="9" r="6" />
                    <path d="M8.5 14.5 7 22l5-3 5 3-1.5-7.5" />
                  </svg>
                </span>
                <span className="min-w-0">
                  <b className="block text-[15px]">{cert.name}</b>
                  <span className="mt-1 block font-mono text-chip text-mute">
                    {cert.issuer} · {cert.year}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      </Container>
    </Section>
  );
}
