const tech = [
  "Java",
  "Spring Boot",
  "Spring AI",
  "Next.js",
  "React",
  "TypeScript",
  "PostgreSQL",
  "pgvector",
  "Docker",
  "Gemini",
  "Ollama",
  "Resilience4j",
];

function Spark() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      className="shrink-0 text-ac1"
    >
      <path d="M12 2l2.4 7.1L22 12l-7.6 2.9L12 22l-2.4-7.1L2 12l7.6-2.9z" />
    </svg>
  );
}

function Track({ hidden = false }: { hidden?: boolean }) {
  return (
    <ul
      aria-hidden={hidden || undefined}
      aria-label={hidden ? undefined : "Technologies I work with"}
      className="flex list-none items-center gap-7 md:gap-10"
    >
      {tech.map((item) => (
        <li
          key={item}
          className="flex items-center gap-7 text-[17px] font-extrabold whitespace-nowrap uppercase text-mute [font-stretch:115%] md:gap-10 md:text-[22px]"
          translate="no"
        >
          {item}
          <Spark />
        </li>
      ))}
    </ul>
  );
}

/**
 * `.strip` — the scrolling band of technologies under the hero.
 *
 * The track holds the list twice so translating it by half its width loops
 * seamlessly; the second copy is `aria-hidden`, so the names are announced
 * once. Under `prefers-reduced-motion` the animation never starts and the
 * strip reads as a static row.
 */
export function TechStrip() {
  return (
    <section
      aria-label="Tech stack"
      className="overflow-hidden border-y border-line py-5.5"
    >
      <div className="flex w-max gap-7 motion-safe:animate-[marquee_36s_linear_infinite] md:gap-10">
        <Track />
        <Track hidden />
      </div>
    </section>
  );
}
