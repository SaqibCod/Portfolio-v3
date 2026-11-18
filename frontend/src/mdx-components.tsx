import { mdxComponents } from "@/components/mdx/MdxComponents";

import type { MDXComponents } from "mdx/types";

/**
 * Required by `@next/mdx` under the App Router: every compiled `.mdx` module
 * calls this to resolve the components it renders with. The map itself lives
 * in `src/components/mdx/MdxComponents.tsx`.
 */
export function useMDXComponents(): MDXComponents {
  return mdxComponents;
}
