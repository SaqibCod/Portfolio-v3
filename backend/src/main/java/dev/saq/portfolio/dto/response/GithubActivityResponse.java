package dev.saq.portfolio.dto.response;

import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.model.GithubActivityType;

/**
 * Recent public activity for the "Currently building" section, matching the contract's
 * {@code GithubActivity}.
 *
 * @param items     newest first, already trimmed to the requested size
 * @param updatedAt when the underlying data was read from GitHub
 */
public record GithubActivityResponse(List<Item> items, Instant updatedAt) {

	/**
	 * One commit, pull request or release.
	 *
	 * @param type       which of the three this is
	 * @param repo       the repository's short name, without the owner — the design shows
	 *                   {@code githmind}, not {@code SaqibCod/githmind}
	 * @param title      the commit headline, pull request title or release name
	 * @param url        the public GitHub URL for it
	 * @param occurredAt when it happened: a commit's authored date, a pull request's merge date
	 *                   (or its open date while unmerged), a release's publication date
	 */
	public record Item(
			GithubActivityType type,
			String repo,
			String title,
			String url,
			Instant occurredAt) {
	}

}
