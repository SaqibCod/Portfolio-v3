"use client";

import { useEffect, useRef, useState } from "react";

import { cn } from "@/lib/cn";

/**
 * Stagger from the export's `:nth-child(2n)` / `:nth-child(3n)` rules, where
 * `3n` wins on the sixth mark because it comes later in the stylesheet.
 * Applied per shape so the result doesn't depend on utility output order.
 */
const WOBBLE_DELAYS = ["0s", "-1.5s", "-3s", "-1.5s", "0s", "-3s", "0s", "-1.5s"];

/**
 * `wob` animates the standalone `rotate` property while these set `transform`,
 * so the two compose instead of fighting. Writing them as `rotate-*`/`scale-*`
 * would lose to the running animation.
 *
 * Both class strings are spelled out in full: Tailwind scans source text for
 * literal candidates, so a composed `hover:${...}` would never be generated.
 */
const SHAPE_CLASS =
  "size-[34px] shrink-0 touch-manipulation [-webkit-tap-highlight-color:transparent] " +
  "transition-transform duration-400 ease-[cubic-bezier(.3,1.6,.5,1)] " +
  "hover:[transform:rotate(24deg)_scale(1.15)] " +
  "motion-safe:animate-[wob_6s_ease-in-out_infinite] md:size-18";

const POKED_CLASS = "[transform:rotate(24deg)_scale(1.15)]";

/**
 * How long a tapped shape holds the transform. A tap is far shorter than the
 * 0.4s spring, so releasing on `pointerup` would cut the motion off before it
 * became visible.
 */
const POKE_MS = 450;

/** The eight marks, in the export's order. */
const SHAPES: React.ReactNode[] = [
  <path
    key="cross"
    d="M16 16L56 56M56 16L16 56"
    stroke="var(--deco-lime)"
    strokeWidth="16"
    strokeLinecap="round"
  />,
  <circle key="disc" cx="36" cy="36" r="30" fill="var(--ac1)" />,
  <path key="dome" d="M6 50A30 30 0 0 1 66 50Z" fill="currentColor" />,
  <g key="quatrefoil" fill="var(--deco-orange)">
    <circle cx="24" cy="24" r="13" />
    <circle cx="48" cy="24" r="13" />
    <circle cx="24" cy="48" r="13" />
    <circle cx="48" cy="48" r="13" />
  </g>,
  <path key="triangle" d="M36 8L66 62H6Z" fill="var(--deco-fuchsia)" />,
  <path
    key="zigzag"
    d="M8 50L22 22L36 50L50 22L64 50"
    fill="none"
    stroke="currentColor"
    strokeWidth="10"
    strokeLinecap="round"
    strokeLinejoin="round"
  />,
  <circle
    key="ring"
    cx="36"
    cy="36"
    r="24"
    fill="none"
    stroke="var(--ac1)"
    strokeWidth="12"
  />,
  <rect
    key="diamond"
    x="16"
    y="16"
    width="40"
    height="40"
    rx="10"
    fill="var(--deco-lime)"
    transform="rotate(45 36 36)"
  />,
];

/**
 * The row of eight geometric marks that closes the footer.
 *
 * Purely decorative, so the row stays hidden from assistive tech and the
 * shapes are not focusable — turning them into controls would announce eight
 * do-nothing buttons. Pointer devices get the export's `:hover` rule; touch
 * devices get the same transform from a tap, since `:hover` never resolves
 * there.
 */
export function FooterShapes() {
  const [poked, setPoked] = useState<number | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  /** Mouse and pen already have hover; only touch needs the tap fallback. */
  function handlePointerDown(
    event: React.PointerEvent<SVGSVGElement>,
    index: number,
  ) {
    if (event.pointerType !== "touch") return;

    if (timer.current) clearTimeout(timer.current);
    setPoked(index);
    timer.current = setTimeout(() => setPoked(null), POKE_MS);
  }

  return (
    <div
      aria-hidden="true"
      className="mt-10 flex justify-between gap-1 text-fg md:gap-3"
    >
      {SHAPES.map((content, index) => (
        <svg
          key={index}
          viewBox="0 0 72 72"
          className={cn(SHAPE_CLASS, poked === index && POKED_CLASS)}
          style={{ animationDelay: WOBBLE_DELAYS[index] }}
          onPointerDown={(event) => handlePointerDown(event, index)}
        >
          {content}
        </svg>
      ))}
    </div>
  );
}
