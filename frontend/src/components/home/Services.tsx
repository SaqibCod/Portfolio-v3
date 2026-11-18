import { TechStackList } from "@/components/projects/TechStackList";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";

const services = [
  {
    title: "Backend engineering",
    description:
      "REST APIs and services in Spring Boot — clean contracts, validation, rate limiting and Problem Details errors.",
    stack: ["Java", "Spring Boot", "PostgreSQL"],
  },
  {
    title: "AI & RAG",
    description:
      "Retrieval-augmented chat over real documents, with streaming answers and source citations.",
    stack: ["Spring AI", "pgvector", "Gemini", "Ollama"],
  },
  {
    title: "Frontend",
    description:
      "Fast, accessible, SEO-friendly interfaces with server rendering where it counts.",
    stack: ["Next.js", "React", "TypeScript"],
  },
  {
    title: "Reliability",
    description:
      "Fallback models, circuit breakers, caching and uptime monitoring so things keep working.",
    stack: ["Resilience4j", "SSE", "WebSocket"],
  },
  {
    title: "Cloud & DevOps",
    description:
      "Containerised deploys and CI pipelines, kept on a zero-cost budget.",
    stack: ["Docker", "CI/CD"],
  },
];

/**
 * `.services` — section 02, on the inverted band. An ordered list because the
 * export numbers each row, and the numbers are the only thing making the
 * order meaningful.
 */
export function Services() {
  return (
    <Section id="skills" inverted rounded className="py-20 md:py-30">
      <Container>
        <div className="mb-10 flex flex-col items-start gap-4 md:mb-14 md:flex-row md:items-end md:justify-between md:gap-8">
          <div>
            <p className="eyebrow">02 — What I do</p>
            <h2 className="display mt-4.5 text-[10vw] md:text-[clamp(44px,7vw,112px)]">
              What I do
            </h2>
          </div>
          <p className="max-w-[340px] text-base leading-relaxed text-mute">
            The parts of the stack I enjoy most, and the tools I reach for.
          </p>
        </div>

        <ol className="list-none">
          {services.map((service, index) => (
            <li
              key={service.title}
              className="grid grid-cols-[64px_minmax(0,1fr)] items-center gap-x-4 gap-y-2 border-t border-line py-6 last:border-b md:grid-cols-[140px_minmax(0,1fr)_minmax(0,1fr)] md:gap-8 md:py-7.5"
            >
              <span
                aria-hidden="true"
                className="display text-4xl leading-none tracking-[-0.03em] md:text-[64px]"
              >
                {String(index + 1).padStart(2, "0")}
              </span>
              <div className="min-w-0">
                <h3 className="text-[22px] font-extrabold uppercase [font-stretch:115%]">
                  {service.title}
                </h3>
                <p className="mt-2 max-w-[420px] text-[15px] leading-relaxed text-mute">
                  {service.description}
                </p>
              </div>
              <TechStackList
                items={service.stack}
                label={`${service.title} tools`}
                className="col-start-2 md:justify-end"
              />
            </li>
          ))}
        </ol>
      </Container>
    </Section>
  );
}
