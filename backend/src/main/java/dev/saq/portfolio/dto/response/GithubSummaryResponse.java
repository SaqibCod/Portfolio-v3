package dev.saq.portfolio.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The contribution graph, language breakdown and totals, matching the contract's
 * {@code GithubSummary}.
 *
 * @param username           the account the figures describe, so the widget can link to it
 *                           without the frontend configuring a second copy of the name
 * @param totalContributions contributions over the last 365 days, straight from GitHub's calendar
 * @param contributions      one entry per day of that year, oldest first
 * @param topLanguages       at most six languages, largest share first
 * @param updatedAt          when these figures were read from GitHub — not when they were served.
 *                           A visitor seeing a six-hour-old value should be able to tell
 */
public record GithubSummaryResponse(
		String username,
		int totalContributions,
		List<ContributionDay> contributions,
		List<Language> topLanguages,
		Instant updatedAt) {

	/**
	 * One day of the contribution graph.
	 *
	 * @param date  the calendar day, serialised as {@code YYYY-MM-DD}
	 * @param count contributions that day; zero days are included, because the graph needs a
	 *              square for every day rather than only for the active ones
	 */
	public record ContributionDay(LocalDate date, int count) {
	}

	/**
	 * One language's share of the code across all public repositories.
	 *
	 * @param name       the language's name
	 * @param percentage share of total bytes, rounded to one decimal place
	 * @param color      GitHub's hex colour for the language, or null if it publishes none
	 */
	public record Language(String name, double percentage, String color) {
	}

}
