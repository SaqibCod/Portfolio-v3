# OG card fonts

Two latin-subset `.woff` files, read at build time by `app/opengraph-image.tsx`
and `app/projects/[slug]/opengraph-image.tsx` so the generated cards use the
site's own type rather than a fallback sans.

They are vendored rather than installed because `next/og` needs the font bytes
on disk, and `next/font/google` only ever produces `.woff2` — which satori, the
renderer behind `ImageResponse`, cannot parse.

| File | Source | Version |
|---|---|---|
| `archivo-latin-900-normal.woff` | `@fontsource/archivo` (`files/archivo-latin-900-normal.woff`) | 5.3.0 |
| `jetbrains-mono-latin-500-normal.woff` | `@fontsource/jetbrains-mono` (`files/jetbrains-mono-latin-500-normal.woff`) | 5.3.0 |

Both faces are licensed under the SIL Open Font License 1.1 — Archivo by
Omnibus-Type, JetBrains Mono by JetBrains. To refresh them, install the two
`@fontsource` packages, copy the files listed above, and remove the packages
again.
