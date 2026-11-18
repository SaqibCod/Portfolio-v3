"use client";

import Image from "next/image";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";

import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { VisuallyHidden } from "@/components/ui/VisuallyHidden";
import { cn } from "@/lib/cn";
import { siteConfig } from "@/lib/site-config";

/**
 * The four portraits. Index 1 is the poke face — it is never reached by
 * clicking, only by hovering, which is why `nextFace` skips it.
 */
const faces = [
  {
    src: "/avatar-smile-clear.png",
    glasses: "clear glasses",
    mood: "Shipping features",
    warn: false,
  },
  {
    src: "/avatar-frown-clear.png",
    glasses: "clear glasses",
    mood: "Debugging prod",
    warn: true,
  },
  {
    src: "/avatar-smile-gold.png",
    glasses: "gold glasses",
    mood: "Reviewing PRs",
    warn: false,
  },
  {
    src: "/avatar-smile-metal.png",
    glasses: "metal glasses",
    mood: "Writing tests",
    warn: false,
  },
] as const;

/**
 * The mood loop, straight from the export: which face, and how long it holds.
 * It lingers on "shipping features" and only flashes the frown.
 */
const sequence = [
  { face: 0, ms: 3200 },
  { face: 1, ms: 1500 },
  { face: 0, ms: 2000 },
  { face: 2, ms: 3000 },
  { face: 3, ms: 3000 },
] as const;

const POKE_FACE = 1;

const reducedMotionQuery = "(prefers-reduced-motion: reduce)";

function subscribeToReducedMotion(onChange: () => void) {
  const media = window.matchMedia(reducedMotionQuery);
  media.addEventListener("change", onChange);
  return () => media.removeEventListener("change", onChange);
}

/**
 * Whether the visitor asked for less motion.
 *
 * Server-rendered as `false` so the markup matches what a browser with motion
 * enabled paints; the real value applies on hydration, before any timer runs.
 */
function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(
    subscribeToReducedMotion,
    () => window.matchMedia(reducedMotionQuery).matches,
    () => false,
  );
}

/**
 * `.facewrap` — the portrait toy.
 *
 * Three behaviours from the export: the mood loop cycles the face on a timer,
 * hovering pokes it into the frown, and clicking changes the glasses. The loop
 * is decorative, so it never starts when the visitor prefers reduced motion —
 * clicking still works, which is the only part that is actually a control.
 */
function Avatar() {
  const prefersReducedMotion = usePrefersReducedMotion();
  const [step, setStep] = useState(0);
  const [poked, setPoked] = useState(false);
  // Announced only for changes the visitor caused. The loop must never
  // interrupt a screen reader every couple of seconds.
  const [announcement, setAnnouncement] = useState("");

  const sequenced = sequence[step % sequence.length].face;
  const index = poked ? POKE_FACE : sequenced;
  const current = faces[index];

  useEffect(() => {
    if (prefersReducedMotion) return;

    const timer = setTimeout(
      () => setStep((previous) => previous + 1),
      sequence[step % sequence.length].ms,
    );

    return () => clearTimeout(timer);
  }, [step, prefersReducedMotion]);

  /** Advances to the next pair of glasses, skipping the frown. */
  const nextFace = useCallback(() => {
    setStep((previous) => {
      let next = previous + 1;
      // Bounded: the sequence holds faces the loop can land on, so a full
      // pass is always enough to find one that is neither the frown nor the
      // face already showing.
      for (let tries = 0; tries < sequence.length; tries += 1) {
        const candidate = sequence[next % sequence.length].face;
        if (candidate !== POKE_FACE && candidate !== index) break;
        next += 1;
      }
      setAnnouncement(faces[sequence[next % sequence.length].face].glasses);
      return next;
    });
    setPoked(false);
  }, [index]);

  return (
    <>
      <button
        type="button"
        onClick={nextFace}
        onMouseEnter={() => setPoked(true)}
        onMouseLeave={() => setPoked(false)}
        // The label carries both the subject and the action: a button's own
        // name overrides any `alt` inside it, so describing the portrait on
        // the images would never be announced.
        aria-label={`Portrait of ${siteConfig.name} — click to change the glasses`}
        className="relative block size-full cursor-pointer overflow-hidden rounded-full border-0 bg-black p-0 shadow-[0_0_0_1px_var(--line),0_40px_90px_-30px_var(--ring)] motion-safe:animate-[float_5.5s_ease-in-out_infinite]"
      >
        {/*
          The base never fades, so a cross-fade can never show a gap. It is the
          one portrait on the LCP path; the overlays load lazily behind it.
        */}
        <Image
          src={faces[0].src}
          alt=""
          fill
          priority
          sizes="(min-width: 47.5rem) 420px, 280px"
          className="object-cover"
        />
        {faces.map((face, faceIndex) => (
          <Image
            key={face.src}
            src={face.src}
            // Decorative: the button around them carries the name.
            alt=""
            fill
            loading="lazy"
            sizes="(min-width: 47.5rem) 420px, 280px"
            className={cn(
              "object-cover transition-[opacity,transform] duration-500 ease-out",
              faceIndex === index ? "scale-100 opacity-100" : "scale-[1.03] opacity-0",
            )}
          />
        ))}
      </button>

      {/* Decorative flavour, duplicated into the live region above only when
          the visitor clicks. */}
      <p
        aria-hidden="true"
        className="absolute bottom-[-22px] left-1/2 flex -translate-x-1/2 items-center gap-2 rounded-full border border-line bg-bg2 px-3.5 py-2.5 font-mono text-eyebrow whitespace-nowrap shadow-lift md:-right-9 md:bottom-14.5 md:left-auto md:translate-x-0"
      >
        <i
          aria-hidden="true"
          className={cn(
            "size-2 shrink-0 rounded-full",
            current.warn ? "bg-warn [--dot-color:var(--warn)]" : "bg-ok [--dot-color:var(--ok)]",
            "motion-safe:animate-[dot-pulse_2s_ease-in-out_infinite]",
          )}
        />
        {current.mood}
      </p>

      <VisuallyHidden aria-live="polite">{announcement}</VisuallyHidden>
    </>
  );
}

/**
 * `.hero` — the first screen: the greeting, the portrait toy, and the one-line
 * pitch beside the primary call to action.
 *
 * Moving the pointer anywhere in the band tilts the portrait. That is written
 * straight to the node's `transform` rather than held in state: a pointer move
 * fires dozens of times a second, and re-rendering the tree for each one would
 * cost far more than the effect is worth.
 */
export function Hero() {
  const prefersReducedMotion = usePrefersReducedMotion();
  const tiltRef = useRef<HTMLDivElement>(null);

  const handleMove = (event: React.MouseEvent<HTMLElement>) => {
    if (prefersReducedMotion || !tiltRef.current) return;

    const bounds = event.currentTarget.getBoundingClientRect();
    const dx = ((event.clientX - bounds.left) / bounds.width - 0.5) * 2;
    const dy = ((event.clientY - bounds.top) / bounds.height - 0.5) * 2;

    tiltRef.current.style.transform = `perspective(900px) rotateY(${(dx * 9).toFixed(2)}deg) rotateX(${(-dy * 7).toFixed(2)}deg)`;
  };

  const handleLeave = () => {
    if (!tiltRef.current) return;
    tiltRef.current.style.transform = "";
  };

  return (
    <section
      id="top"
      onMouseMove={handleMove}
      onMouseLeave={handleLeave}
      className="relative pt-9 pb-12 text-center md:pt-14 md:pb-16"
    >
      <Container>
        {/* The space between the spans is a text node of its own, so the
            accessible name reads "Hi, I’m Saq" — a trailing space inside the
            first span would be trimmed away. Below the breakpoint each span
            becomes a block and the space collapses, as in the export. */}
        <h1 className="display metal relative z-1 text-[14vw] md:text-[clamp(44px,9.6vw,150px)] [&>span]:max-md:block">
          <span>Hi, I’m</span> <span>{siteConfig.name}</span>
        </h1>

        <div
          ref={tiltRef}
          className="relative z-2 mx-auto mt-[-6vw] size-70 transition-transform duration-250 ease-out md:mt-[-5.5vw] md:size-105"
        >
          <Avatar />
        </div>

        <p className="mt-4.5 font-mono text-chip text-mute">
          ( hover to poke · click to change glasses )
        </p>

        <div className="mt-7 flex flex-col items-stretch gap-6 text-left md:mt-10 md:flex-row md:items-end md:justify-between md:gap-8">
          <p className="max-w-[360px] font-mono text-eyebrow leading-relaxed tracking-[0.04em] uppercase text-mute">
            {siteConfig.tagline}
          </p>
          <ButtonLink href="/#contact" variant="primary" className="max-md:w-full">
            Contact me
          </ButtonLink>
        </div>
      </Container>
    </section>
  );
}
