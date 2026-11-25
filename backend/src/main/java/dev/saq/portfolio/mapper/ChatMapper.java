package dev.saq.portfolio.mapper;

import dev.saq.portfolio.dto.response.ChatSourceResponse;
import dev.saq.portfolio.service.KnowledgeIngestionService;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * Turns a retrieved chunk into the citation the chat widget renders.
 *
 * <p>Everything a citation needs travels on the chunk's own metadata, written at ingestion time.
 * Nothing is looked up here, which is what makes a citation trustworthy: the title and url shown
 * belong to the chunk that was actually in the prompt, not to a page someone matched up
 * afterwards.
 */
@Component
public class ChatMapper {

	/** The contract's {@code ChatSource.snippet} maxLength. */
	private static final int MAX_SNIPPET_CHARS = 300;

	/**
	 * One chunk as a citation.
	 *
	 * <p>Metadata values are read defensively. A chunk embedded by an older version of the
	 * ingestion service could be missing a key, and a missing title is worth a citation with a
	 * plain label — not a 500 in the middle of a stream that has already begun.
	 */
	public ChatSourceResponse toSource(Document chunk) {
		String title = metadata(chunk, KnowledgeIngestionService.TITLE_METADATA);
		String url = metadata(chunk, KnowledgeIngestionService.URL_METADATA);
		return new ChatSourceResponse(title.isBlank() ? "This site" : title, url, snippet(chunk));
	}

	/** The page a chunk cites, or empty when its knowledge file declared none. */
	public String urlOf(Document chunk) {
		return metadata(chunk, KnowledgeIngestionService.URL_METADATA);
	}

	/**
	 * The opening of a chunk, as one line, under the contract's length cap.
	 *
	 * <p>Whitespace is collapsed because a chunk is markdown: left alone, a snippet would carry
	 * heading hashes on their own lines and read as broken text inside a 300-character chip.
	 * Truncation lands on a word boundary and ends with an ellipsis, so a cut is visibly a cut.
	 */
	private static String snippet(Document chunk) {
		String text = chunk.getText() == null ? "" : chunk.getText();
		String flattened = text.replaceAll("\\s+", " ").strip();
		if (flattened.length() <= MAX_SNIPPET_CHARS) {
			return flattened;
		}

		String cut = flattened.substring(0, MAX_SNIPPET_CHARS - 1);
		int lastSpace = cut.lastIndexOf(' ');
		return (lastSpace > 0 ? cut.substring(0, lastSpace) : cut) + "…";
	}

	private static String metadata(Document chunk, String key) {
		Object value = chunk.getMetadata().get(key);
		return value == null ? "" : value.toString();
	}

}
