import Image from "next/image";
import Link from "next/link";

import { DemoEmbed, DemoSlot } from "@/components/demo/DemoEmbed";
import { Badge } from "@/components/ui/Badge";
import { cn } from "@/lib/cn";

import type { MDXComponents } from "mdx/types";

/** Turns heading text into the anchor the on-this-page nav links to. */
export function headingId(children: React.ReactNode): string | undefined {
  if (typeof children !== "string") return undefined;
  return children
    .toLowerCase()
    .replace(/&/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

/* --------------------------------------------------------------------------
   Element overrides — the `.sec` block in the case-study export.
   -------------------------------------------------------------------------- */

function H2({ children, ...props }: React.ComponentPropsWithoutRef<"h2">) {
  return (
    <h2
      id={headingId(children)}
      // The scroll margin has to clear whatever is pinned above: just the
      // header on desktop, but the header plus the sticky on-this-page pill
      // row below the breakpoint. The hairline above each section is the
      // export's `.sec` divider.
      className="display mt-18 scroll-mt-35 border-t border-line pt-18 text-[clamp(32px,4vw,52px)] first:mt-0 first:border-0 first:pt-0 md:scroll-mt-nav"
      {...props}
    >
      {children}
    </h2>
  );
}

function H3({ children, ...props }: React.ComponentPropsWithoutRef<"h3">) {
  return (
    <h3
      id={headingId(children)}
      className="mt-10 scroll-mt-nav text-lg font-extrabold uppercase [font-stretch:115%]"
      {...props}
    >
      {children}
    </h3>
  );
}

function Paragraph(props: React.ComponentPropsWithoutRef<"p">) {
  return <p className="mt-5 text-[18px] leading-relaxed" {...props} />;
}

function Anchor({ href = "", ...props }: React.ComponentPropsWithoutRef<"a">) {
  const external = /^(https?:)?\/\//.test(href) || href.startsWith("mailto:");

  if (external) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noreferrer"
        className="underline underline-offset-4"
        {...props}
      />
    );
  }

  return (
    <Link href={href} className="underline underline-offset-4" {...props} />
  );
}

function UnorderedList(props: React.ComponentPropsWithoutRef<"ul">) {
  return (
    <ul
      className="mt-5 flex list-disc flex-col gap-2.5 pl-6 text-[17px] leading-relaxed"
      {...props}
    />
  );
}

function OrderedList(props: React.ComponentPropsWithoutRef<"ol">) {
  return (
    <ol
      className="mt-5 flex list-decimal flex-col gap-2.5 pl-6 text-[17px] leading-relaxed"
      {...props}
    />
  );
}

function InlineCode(props: React.ComponentPropsWithoutRef<"code">) {
  return (
    <code
      className="rounded-[6px] border border-line bg-bg2 px-1.5 py-0.5 font-mono text-[0.9em]"
      {...props}
    />
  );
}

/**
 * A fenced code block without a filename header. `CodeBlock` wraps the same
 * surface with the export's header bar when there is a file to name.
 */
function Pre(props: React.ComponentPropsWithoutRef<"pre">) {
  return (
    <pre
      className="mt-6 overflow-x-auto rounded-lg border border-line bg-code px-4.5 py-5 font-mono text-[13.5px] leading-[1.75] text-zinc-200"
      {...props}
    />
  );
}

function Blockquote(props: React.ComponentPropsWithoutRef<"blockquote">) {
  return (
    <blockquote
      className="mt-6 rounded-md border border-l-3 border-line border-l-ac1 bg-bg2 px-5 py-4 text-[17px] leading-relaxed text-mute [&>p+p]:mt-3 [&>p]:mt-0"
      {...props}
    />
  );
}

function Hr(props: React.ComponentPropsWithoutRef<"hr">) {
  return <hr className="mt-12 border-line" {...props} />;
}

type MdxImageProps = React.ComponentPropsWithoutRef<"img"> & {
  width?: number | string;
  height?: number | string;
};

/**
 * Markdown images carry no dimensions, so a 16:9 intrinsic size is assumed
 * unless the author passes one. Explicit width and height are what keep the
 * page from shifting as images arrive.
 */
function MdxImage({ src, alt, width, height, ...props }: MdxImageProps) {
  if (typeof src !== "string") return null;

  return (
    <Image
      src={src}
      alt={alt ?? ""}
      width={Number(width ?? 1200)}
      height={Number(height ?? 675)}
      sizes="(min-width: 47.5rem) 760px, 100vw"
      loading="lazy"
      className="mt-6 h-auto w-full rounded-lg border border-line"
      {...props}
    />
  );
}

/* --------------------------------------------------------------------------
   Blocks the case-study template uses. Each one mirrors a piece of the
   export, and all of them are available in MDX without an import.
   -------------------------------------------------------------------------- */

type FigureProps = {
  src: string;
  alt: string;
  width?: number;
  height?: number;
  caption?: string;
};

/** An image with a mono caption, for diagrams and screenshots. */
export function Figure({
  src,
  alt,
  width = 1200,
  height = 675,
  caption,
}: FigureProps) {
  return (
    <figure className="mt-6">
      <Image
        src={src}
        alt={alt}
        width={width}
        height={height}
        sizes="(min-width: 47.5rem) 760px, 100vw"
        loading="lazy"
        className="h-auto w-full rounded-lg border border-line"
      />
      {caption ? (
        <figcaption className="mt-3.5 font-mono text-chip text-mute">
          {caption}
        </figcaption>
      ) : null}
    </figure>
  );
}

type MetricTilesProps = {
  items: { value: string; label: string }[];
};

/** `.tiles` — the three headline numbers under the overview. */
export function MetricTiles({ items }: MetricTilesProps) {
  return (
    <dl className="mt-7 grid gap-3.5 md:grid-cols-3">
      {items.map((item) => (
        <div
          key={item.label}
          className="rounded-lg border border-line bg-bg2 p-5"
        >
          <dt className="sr-only">{item.label}</dt>
          <dd>
            <b className="display block text-3xl [font-stretch:125%]">
              {item.value}
            </b>
            <span className="mt-2.5 block text-sm leading-normal text-mute">
              {item.label}
            </span>
          </dd>
        </div>
      ))}
    </dl>
  );
}

type DecisionsProps = {
  items: { title: string; body: string }[];
};

/** `.dec` — numbered decisions with the stroked outline numeral. */
export function Decisions({ items }: DecisionsProps) {
  return (
    <ol className="mt-6 list-none">
      {items.map((item, index) => (
        <li
          key={item.title}
          className="grid grid-cols-[56px_minmax(0,1fr)] gap-4 border-t border-line py-5.5 first:border-0 first:pt-0"
        >
          <span
            aria-hidden="true"
            className="display text-[34px] leading-none text-transparent [-webkit-text-stroke:1.5px_var(--fg)]"
          >
            {String(index + 1).padStart(2, "0")}
          </span>
          <div>
            <h3 className="text-[19px] font-extrabold">{item.title}</h3>
            <p className="mt-1.5 text-base leading-relaxed text-mute">
              {item.body}
            </p>
          </div>
        </li>
      ))}
    </ol>
  );
}

type DiagramNode = {
  title: string;
  sub: string;
  /** Highlights the node the request is really about, like `.node.acc`. */
  accent?: boolean;
};

type ArchDiagramProps = {
  /** Describes the whole diagram to screen readers; required. */
  label: string;
  rows: DiagramNode[][];
  caption?: string;
};

function Arrow() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="shrink-0 text-mute max-md:rotate-90"
    >
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

/**
 * `.dia` — the boxes-and-arrows walk-through of a request. The whole figure
 * carries one `aria-label`, because reading the boxes in isolation tells a
 * screen-reader user nothing about how they connect.
 */
export function ArchDiagram({ label, rows, caption }: ArchDiagramProps) {
  return (
    <figure className="mt-6 rounded-card border border-line bg-bg2 p-7">
      <div role="img" aria-label={label} className="flex flex-col gap-2.5">
        {rows.map((row, rowIndex) => (
          <div
            key={rowIndex}
            className="flex flex-col items-stretch gap-2.5 md:flex-row md:items-center"
          >
            {row.map((node, nodeIndex) => (
              <div key={node.title} className="contents">
                {nodeIndex > 0 ? (
                  <span className="self-center md:self-auto">
                    <Arrow />
                  </span>
                ) : null}
                <div
                  className={cn(
                    "min-w-0 flex-1 rounded-input border border-line bg-bg p-3.5 text-center",
                    node.accent && "border-ac1 shadow-[0_0_0_3px_var(--ring)]",
                  )}
                >
                  <b className="block text-sm font-bold">{node.title}</b>
                  <span className="mt-1 block font-mono text-[11px] text-mute">
                    {node.sub}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ))}
      </div>
      {caption ? (
        <figcaption className="mt-3.5 font-mono text-chip text-mute">
          {caption}
        </figcaption>
      ) : null}
    </figure>
  );
}

type CodeBlockProps = {
  /** Shown in the header bar, e.g. "ChatController.java". */
  filename: string;
  code: string;
};

/**
 * `.code` — the dark panel with a filename header. The code arrives as a
 * string so the panel owns its own `pre` and the surrounding prose styles
 * cannot double up on it.
 */
export function CodeBlock({ filename, code }: CodeBlockProps) {
  return (
    <div className="mt-6 overflow-hidden rounded-lg border border-line bg-code">
      <div className="flex items-center justify-between gap-3 border-b border-inv-line px-4.5 py-3">
        <span className="font-mono text-chip text-zinc-400" translate="no">
          {filename}
        </span>
      </div>
      <pre className="overflow-x-auto px-4.5 py-5 font-mono text-[13.5px] leading-[1.75] text-zinc-200">
        <code translate="no">{code}</code>
      </pre>
    </div>
  );
}

type TwoUpProps = {
  items: { eyebrow: string; body: string }[];
};

/** `.two` — the paired "hardest bug" / "what I learned" cards. */
export function TwoUp({ items }: TwoUpProps) {
  return (
    <div className="mt-6 grid gap-3.5 md:grid-cols-2">
      {items.map((item) => (
        <div
          key={item.eyebrow}
          className="rounded-lg border border-line bg-bg2 p-5.5"
        >
          <p className="eyebrow mb-2.5">{item.eyebrow}</p>
          <p className="text-base leading-relaxed text-mute">{item.body}</p>
        </div>
      ))}
    </div>
  );
}

function Tick() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="mt-1 shrink-0 text-ac1"
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

type TicksProps = {
  items: string[];
};

/** `.ticks` — the "what I would do next" list. */
export function Ticks({ items }: TicksProps) {
  return (
    <ul className="mt-5 flex list-none flex-col gap-3.5">
      {items.map((item) => (
        <li
          key={item}
          className="flex items-start gap-3.5 text-[17px] leading-normal"
        >
          <Tick />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}

type ChipsProps = {
  items: string[];
};

/** A row of the design's hairline chips, for inline tech lists. */
export function Chips({ items }: ChipsProps) {
  return (
    <div className="mt-5 flex flex-wrap gap-2">
      {items.map((item) => (
        <Badge key={item} translate="no">
          {item}
        </Badge>
      ))}
    </div>
  );
}

/**
 * Everything MDX gets: the element overrides plus the blocks above, so a case
 * study never needs an import statement.
 */
export const mdxComponents: MDXComponents = {
  h2: H2,
  h3: H3,
  p: Paragraph,
  a: Anchor,
  ul: UnorderedList,
  ol: OrderedList,
  code: InlineCode,
  pre: Pre,
  blockquote: Blockquote,
  hr: Hr,
  img: MdxImage,
  ArchDiagram,
  Chips,
  CodeBlock,
  Decisions,
  DemoEmbed,
  DemoSlot,
  Figure,
  MetricTiles,
  Ticks,
  TwoUp,
};
