import createMDX from "@next/mdx";

import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /**
   * Case studies live in `src/content/projects/*.mdx` and are imported by
   * `src/lib/projects.ts`, so `.mdx` has to be a compilable extension. It is
   * deliberately not a routable one — no MDX file sits under `src/app`.
   */
  pageExtensions: ["ts", "tsx", "md", "mdx"],
};

/**
 * No remark or rehype plugins: the case-study template is plain markdown plus
 * the components exported from `src/components/mdx/MdxComponents.tsx`.
 */
const withMDX = createMDX({});

export default withMDX(nextConfig);
