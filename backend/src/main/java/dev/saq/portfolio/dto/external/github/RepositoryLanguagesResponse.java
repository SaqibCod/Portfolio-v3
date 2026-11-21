package dev.saq.portfolio.dto.external.github;

import java.util.List;

/**
 * GitHub's answer to the language-bytes query: every owned public non-fork repository with the
 * number of bytes written in each language.
 *
 * <p>Per-repository figures, not a per-account total — GitHub publishes no such total, so
 * {@link dev.saq.portfolio.mapper.GithubMapper} sums the bytes across repositories itself.
 */
public record RepositoryLanguagesResponse(Data data, List<GraphQlError> errors) {

	public record Data(User user) {
	}

	public record User(Repositories repositories) {
	}

	public record Repositories(List<Repository> nodes) {
	}

	public record Repository(String name, Languages languages) {
	}

	public record Languages(List<LanguageEdge> edges) {
	}

	/**
	 * One language in one repository.
	 *
	 * @param size bytes of code written in the language, which is what the share is weighted by
	 * @param node the language itself
	 */
	public record LanguageEdge(long size, Language node) {
	}

	/**
	 * @param name  the language's name, e.g. {@code Java}
	 * @param color GitHub's hex colour for it, e.g. {@code #b07219}; null for a few languages
	 */
	public record Language(String name, String color) {
	}

}
