import { siteConfig, type NavItem } from "@/lib/site-config";

/** The route that only exists once a project is being monitored. */
export const STATUS_PATH = "/status";

/**
 * Drops `/status` from a link list when there is no status page to link to.
 *
 * The link has to be absent from the server-rendered HTML, not merely hidden:
 * a crawler that follows a link to a 404 learns that the site advertises pages
 * it does not have, and a reader who clicks one learns the same thing faster.
 * So this runs where the markup is built rather than in the browser.
 *
 * Every other entry is untouched, and the order is preserved — adding the first
 * monitored project puts Status back exactly where the design has it.
 */
export function navWithoutStatus(items: readonly NavItem[]): NavItem[] {
  return items.filter((item) => item.href !== STATUS_PATH);
}

/** The primary nav, with Status included only when the page is published. */
export function primaryNav(statusPublished: boolean): NavItem[] {
  return statusPublished
    ? [...siteConfig.nav]
    : navWithoutStatus(siteConfig.nav);
}

/** The footer's "Site" column, under the same rule. */
export function footerSiteNav(statusPublished: boolean): NavItem[] {
  return statusPublished
    ? [...siteConfig.footerSite]
    : navWithoutStatus(siteConfig.footerSite);
}
