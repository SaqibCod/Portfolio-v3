package dev.saq.portfolio.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kinds of activity the "Currently building" list shows.
 *
 * <p>Nothing is persisted: these are domain values that exist only between GitHub and the API's
 * own JSON. The contract spells them {@code commit}, {@code pull_request} and {@code release},
 * and {@link #toApi()} is the only place that mapping lives.
 */
public enum GithubActivityType {

	/** A commit on a repository's default branch. */
	COMMIT("commit"),

	/** A pull request this account opened — reported when merged if it has been. */
	PULL_REQUEST("pull_request"),

	/** A published, non-draft release. */
	RELEASE("release");

	private final String apiValue;

	GithubActivityType(String apiValue) {
		this.apiValue = apiValue;
	}

	/**
	 * The spelling the contract uses.
	 *
	 * <p>{@code @JsonValue} so a response record can hold the enum and still serialise to the
	 * contract's string, which keeps the type checked everywhere above the JSON boundary.
	 */
	@JsonValue
	public String toApi() {
		return apiValue;
	}

}
