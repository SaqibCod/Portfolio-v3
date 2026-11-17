import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { ThemeToggle } from "@/components/theme/ThemeToggle";

function renderToggle(variant?: "icon" | "chip") {
  return render(
    <ThemeProvider>
      <ThemeToggle variant={variant} />
    </ThemeProvider>,
  );
}

afterEach(() => {
  window.localStorage.clear();
  document.documentElement.className = "";
});

describe("ThemeToggle", () => {
  it("puts the dark class on <html> and takes it off again", async () => {
    const user = userEvent.setup();
    renderToggle();

    // jsdom reports no prefers-color-scheme match, so the system theme
    // resolves to light and the first press switches to dark.
    await user.click(
      await screen.findByRole("button", { name: "Switch to dark theme" }),
    );
    expect(document.documentElement.classList.contains("dark")).toBe(true);

    await user.click(
      await screen.findByRole("button", { name: "Switch to light theme" }),
    );
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(document.documentElement.classList.contains("light")).toBe(true);
  });

  it("labels the press with the theme it moves to", async () => {
    renderToggle();

    const button = await screen.findByRole("button", {
      name: "Switch to dark theme",
    });
    expect(button).toHaveAttribute("aria-label", "Switch to dark theme");
  });

  it("remembers the choice", async () => {
    const user = userEvent.setup();
    renderToggle();

    await user.click(
      await screen.findByRole("button", { name: "Switch to dark theme" }),
    );

    expect(window.localStorage.getItem("theme")).toBe("dark");
  });

  it("renders the footer chip variant with a readable label", async () => {
    renderToggle("chip");

    expect(
      await screen.findByRole("button", { name: "Switch to dark theme" }),
    ).toHaveTextContent("Switch to dark");
  });
});
