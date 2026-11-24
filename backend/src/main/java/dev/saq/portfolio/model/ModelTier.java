package dev.saq.portfolio.model;

/**
 * Which of the two language models answered a question.
 *
 * <p>Reported to the browser in the SSE {@code done} event, because a silent downgrade is worse
 * than a visible one: if the primary model is rate limited for a week, the page should say an
 * answer came from the fallback rather than quietly getting worse.
 */
public enum ModelTier {

	/** Gemini, configured under {@code spring.ai.google.genai}. */
	PRIMARY("primary"),

	/** The OpenAI-compatible endpoint configured by {@code FALLBACK_LLM_*}. */
	FALLBACK("fallback");

	private final String apiValue;

	ModelTier(String apiValue) {
		this.apiValue = apiValue;
	}

	/**
	 * The value the contract puts in {@code done.model}.
	 *
	 * <p>Published, so a client can branch on it; never change one of these without changing the
	 * contract in the same commit.
	 */
	public String apiValue() {
		return apiValue;
	}

}
