package dev.saq.portfolio.dto.external.github;

import java.time.LocalDate;
import java.util.List;

/**
 * GitHub's answer to the contribution-calendar query, mirrored field for field.
 *
 * <p>The calendar is only available through GraphQL — there is no REST equivalent — and it always
 * covers the last year, which is what the contract's {@code totalContributions} means.
 *
 * <p>Every level is nullable because GraphQL nulls a whole subtree when it reports an error: an
 * unknown login makes {@code user} null while the HTTP status stays 200.
 */
public record ContributionCalendarResponse(Data data, List<GraphQlError> errors) {

	public record Data(User user) {
	}

	public record User(ContributionsCollection contributionsCollection) {
	}

	public record ContributionsCollection(ContributionCalendar contributionCalendar) {
	}

	public record ContributionCalendar(int totalContributions, List<Week> weeks) {
	}

	public record Week(List<Day> contributionDays) {
	}

	/**
	 * One square.
	 *
	 * @param date              the day it represents
	 * @param contributionCount how many contributions were made that day
	 */
	public record Day(LocalDate date, int contributionCount) {
	}

}
