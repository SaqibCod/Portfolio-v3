package dev.saq.portfolio.dto.event;

import java.util.List;

import dev.saq.portfolio.dto.response.ChatSourceResponse;

/**
 * The payload of the SSE {@code sources} event: the pages the answer was grounded in.
 *
 * <p>Sent once, after the last token. The order is deliberate — citations shown before an answer
 * exists would invite reading them as the answer, and the retrieval that produced them is what
 * the answer was built from, so they are complete from the start and cost nothing to hold back.
 *
 * <p>May be empty. That is the honest case for a question the site does not cover: no chunk
 * cleared the similarity threshold, so the model was told it had nothing and said so, and there
 * is nothing to cite.
 *
 * @param sources deduplicated by url, most relevant first
 */
public record SourcesEvent(List<ChatSourceResponse> sources) {

}
