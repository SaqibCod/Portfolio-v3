import { readFile } from "node:fs/promises";
import { join } from "node:path";

/** Facebook's and X's preferred card size. */
export const ogSize = { width: 1200, height: 630 };

export const ogContentType = "image/png";

/**
 * Satori renders to a PNG outside the browser, so it cannot read the CSS
 * variables in `globals.css` and every colour has to be literal. These are
 * the dark-theme token values — keep them in step with `:root`/`.dark` there.
 */
const colors = {
  bg: "#08080a",
  fg: "#f4f4f5",
  mute: "#a1a1aa",
  line: "#2a2a30",
  ac1: "#8b5cf6",
  ac2: "#d946ef",
};

const fontDir = join(process.cwd(), "src/assets/fonts");

/**
 * The two faces the cards use. Read once per build; `ImageResponse` needs the
 * raw bytes, and satori cannot parse the `.woff2` that `next/font` produces.
 */
export async function loadOgFonts() {
  const [display, mono] = await Promise.all([
    readFile(join(fontDir, "archivo-latin-900-normal.woff")),
    readFile(join(fontDir, "jetbrains-mono-latin-500-normal.woff")),
  ]);

  return [
    {
      name: "Archivo",
      data: display,
      style: "normal" as const,
      weight: 900 as const,
    },
    {
      name: "JetBrains Mono",
      data: mono,
      style: "normal" as const,
      weight: 500 as const,
    },
  ];
}

type OgCardProps = {
  /** Small mono label above the title, e.g. "Case study". */
  eyebrow: string;
  /** The headline. Kept short; long titles wrap to two lines. */
  title: string;
  /** One line under the title. */
  subtitle: string;
  /** Mono line along the bottom, usually the domain. */
  footer: string;
};

/**
 * The shared branded card: dark ground, violet bloom, uppercase display
 * title. Every style is inline because satori supports no stylesheets, and
 * every container sets `display: flex` because it has no block layout.
 */
export function OgCard({ eyebrow, title, subtitle, footer }: OgCardProps) {
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: "72px 80px",
        backgroundColor: colors.bg,
        color: colors.fg,
        fontFamily: "Archivo",
        position: "relative",
      }}
    >
      {/* The hero bloom, clipped by the card edge */}
      <div
        style={{
          position: "absolute",
          top: -220,
          right: -160,
          width: 620,
          height: 620,
          borderRadius: 9999,
          backgroundImage: `radial-gradient(circle at 32% 28%, ${colors.ac2} 0%, ${colors.ac1} 45%, ${colors.bg} 72%)`,
          opacity: 0.45,
        }}
      />

      <div style={{ display: "flex", flexDirection: "column" }}>
        <div
          style={{
            fontFamily: "JetBrains Mono",
            fontSize: 24,
            letterSpacing: 2,
            textTransform: "uppercase",
            color: colors.mute,
          }}
        >
          {eyebrow}
        </div>
        <div
          style={{
            marginTop: 28,
            fontSize: title.length > 28 ? 84 : 112,
            lineHeight: 1,
            letterSpacing: -3,
            textTransform: "uppercase",
            maxWidth: 940,
          }}
        >
          {title}
        </div>
        <div
          style={{
            marginTop: 32,
            fontFamily: "JetBrains Mono",
            fontSize: 28,
            lineHeight: 1.45,
            color: colors.mute,
            maxWidth: 820,
          }}
        >
          {subtitle}
        </div>
      </div>

      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 20,
          paddingTop: 28,
          borderTop: `1px solid ${colors.line}`,
          fontFamily: "JetBrains Mono",
          fontSize: 24,
          color: colors.mute,
        }}
      >
        <div
          style={{
            width: 56,
            height: 8,
            borderRadius: 9999,
            backgroundImage: `linear-gradient(90deg, ${colors.ac1}, ${colors.ac2})`,
          }}
        />
        {footer}
      </div>
    </div>
  );
}
