package dev.saq.portfolio.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import dev.saq.portfolio.dto.response.ChatSourceResponse;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * Chunk metadata to citation, including the cases where the metadata is not what it should be.
 *
 * <p>A mapper that threw here would take down a stream that had already started answering, so
 * every missing-key case has a defined, boring result.
 */
class ChatMapperTest {

	private final ChatMapper mapper = new ChatMapper();

	@Test
	void carriesTheTitleAndUrlFromTheChunk() {
		ChatSourceResponse source = mapper.toSource(chunk("githMind indexes a repository.",
				Map.of("title", "githMind case study", "url", "/projects/githmind")));

		assertThat(source.title()).isEqualTo("githMind case study");
		assertThat(source.url()).isEqualTo("/projects/githmind");
	}

	@Test
	void flattensMarkdownWhitespaceIntoOneLine() {
		ChatSourceResponse source = mapper.toSource(chunk("""
				# Heading

				A paragraph
				split across lines.
				""", Map.of("title", "T", "url", "/t")));

		// A snippet is rendered inside a chip. Left as markdown it would carry heading hashes
		// and hard wraps and read as broken text.
		assertThat(source.snippet()).isEqualTo("# Heading A paragraph split across lines.");
	}

	@Test
	void keepsTheSnippetWithinTheContractsLength() {
		ChatSourceResponse source = mapper.toSource(
				chunk(("word ".repeat(200)).strip(), Map.of("title", "T", "url", "/t")));

		// ChatSource.snippet is maxLength 300 in the contract. A longer value would make the
		// response fail its own schema.
		assertThat(source.snippet()).hasSizeLessThanOrEqualTo(300);
	}

	@Test
	void truncatesOnAWordBoundaryAndSaysSo() {
		ChatSourceResponse source = mapper.toSource(
				chunk(("alphabet ".repeat(60)).strip(), Map.of("title", "T", "url", "/t")));

		assertThat(source.snippet()).endsWith("…");
		// Cutting mid-word looks like corrupted data rather than an abbreviation.
		assertThat(source.snippet()).doesNotContain("alphab…");
	}

	@Test
	void leavesAShortSnippetAlone() {
		ChatSourceResponse source = mapper.toSource(
				chunk("Short and complete.", Map.of("title", "T", "url", "/t")));

		assertThat(source.snippet()).isEqualTo("Short and complete.");
	}

	@Test
	void labelsAChunkWithNoTitleRatherThanFailing() {
		ChatSourceResponse source = mapper.toSource(chunk("content", Map.of("url", "/t")));

		// Chunks embedded by an earlier version of the ingestion service could be missing a key.
		// A plain label is worth a citation; a 500 mid-stream is not.
		assertThat(source.title()).isEqualTo("This site");
	}

	@Test
	void reportsNoUrlForAChunkThatHasNone() {
		assertThat(mapper.urlOf(chunk("content", Map.of("title", "T")))).isEmpty();
	}

	private static Document chunk(String text, Map<String, Object> metadata) {
		return new Document(text, metadata);
	}

}
