package dev.saq.portfolio.dto.external.github;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub's answer to the recent-activity query: commits, pull requests and releases, with the
 * titles and timestamps the "Currently building" list shows.
 *
 * <p>GraphQL rather than {@code GET /users/{u}/events/public}, which the LLD originally named.
 * GitHub trimmed the Events API payloads on 7 October 2025: a {@code PushEvent} now carries only
 * {@code repository_id}, {@code push_id}, {@code ref}, {@code head} and {@code before} — no
 * commit messages — and a {@code PullRequestEvent}'s {@code pull_request} is reduced to
 * {@code url}, {@code id}, {@code number}, {@code head} and {@code base}, so not even a pull
 * request title survives. Events can therefore no longer describe activity in words, and every
 * field below is read from GraphQL instead.
 *
 * <p>Commits come from the most recently pushed repositories rather than from a global feed,
 * which is the one behavioural difference: activity in somebody else's repository shows up as a
 * pull request (which is what {@code pullRequests} covers) but not as individual commits.
 */
public record RecentActivityResponse(Data data, List<GraphQlError> errors) {

	public record Data(User user) {
	}

	public record User(Repositories repositories, PullRequests pullRequests) {
	}

	public record Repositories(List<Repository> nodes) {
	}

	/**
	 * One owned public repository.
	 *
	 * @param defaultBranchRef null for a repository with no commits at all
	 */
	public record Repository(String name, DefaultBranchRef defaultBranchRef, Releases releases) {
	}

	public record DefaultBranchRef(Target target) {
	}

	/** The branch tip. Only the {@code Commit} case is asked for, so everything else is null. */
	public record Target(History history) {
	}

	public record History(List<Commit> nodes) {
	}

	/**
	 * @param messageHeadline the commit message's first line, which is the activity title
	 * @param committedDate   when it was authored; {@code pushedDate} was removed by GitHub in 2023
	 */
	public record Commit(String messageHeadline, Instant committedDate, String url) {
	}

	public record Releases(List<Release> nodes) {
	}

	/**
	 * @param name        the release title, which may be empty — {@code tagName} is the fallback
	 * @param publishedAt null while a release is still a draft
	 * @param isDraft     drafts are invisible to the public and are dropped by the mapper
	 */
	public record Release(
			String name,
			String tagName,
			String url,
			Instant publishedAt,
			@JsonProperty("isDraft") boolean isDraft) {
	}

	public record PullRequests(List<PullRequest> nodes) {
	}

	/**
	 * A pull request this account opened, in any repository.
	 *
	 * @param mergedAt null while the pull request is still open; when set it is the moment worth
	 *                 reporting, since merging is the event a reader cares about
	 */
	public record PullRequest(
			String title,
			String url,
			Instant createdAt,
			Instant mergedAt,
			PullRequestRepository repository) {
	}

	/**
	 * @param isPrivate private repositories are out of scope; the mapper drops them even though a
	 *                  public-read token should never have returned one
	 */
	public record PullRequestRepository(
			String name,
			@JsonProperty("isPrivate") boolean isPrivate) {
	}

}
