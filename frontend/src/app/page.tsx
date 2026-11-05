import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Portfolio",
  description: "Placeholder home page. Real content arrives in phase 03.",
  alternates: { canonical: "/" },
};

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-4 p-8 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">Portfolio</h1>
      <p className="text-base text-zinc-600 dark:text-zinc-400">
        Scaffold is up. Content lands in later phases.
      </p>
    </main>
  );
}
