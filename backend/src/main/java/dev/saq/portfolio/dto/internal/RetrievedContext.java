package dev.saq.portfolio.dto.internal;

import java.util.List;

import dev.saq.portfolio.dto.response.ChatSourceResponse;

/**
 * What retrieval found for one question: the chunk texts to put in the prompt, and the pages to
 * cite for them.
 *
 * <p>Two lists rather than one, because they are not the same length and must not be. Several
 * chunks of one long page all deserve to be in the prompt, but the visitor should see that page
 * cited once — so {@code chunks} is per-chunk and {@code sources} is deduplicated by url.
 *
 * <p>Both empty is the normal answer for a question the site does not cover, and the case the
 * whole feature's honesty rests on: the model is handed no context, so it has nothing to answer
 * from and says so.
 *
 * @param chunks  retrieved chunk texts, most relevant first
 * @param sources pages to cite, deduplicated by url, in the order their best chunk ranked
 */
public record RetrievedContext(
		List<String> chunks,
		List<ChatSourceResponse> sources) {

	private static final RetrievedContext EMPTY = new RetrievedContext(List.of(), List.of());

	/** Nothing cleared the similarity threshold. */
	public static RetrievedContext empty() {
		return EMPTY;
	}

	/** True when the model will be given no context at all. */
	public boolean isEmpty() {
		return chunks.isEmpty();
	}

}
