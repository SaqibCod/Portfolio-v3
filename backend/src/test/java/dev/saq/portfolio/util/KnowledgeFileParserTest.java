package dev.saq.portfolio.util;

import static org.assertj.core.api.Assertions.assertThat;

import dev.saq.portfolio.dto.internal.KnowledgeFile;

import org.junit.jupiter.api.Test;

/**
 * Frontmatter parsing and hashing, which together decide what a citation says and whether a
 * restart costs anything.
 *
 * <p>The hash cases matter more than they look. Ingestion skips any file whose hash matches the
 * stored one, so a hash that changed for a reason unrelated to content — a checkout with CRLF
 * line endings, say — would re-embed the whole knowledge base on every machine.
 */
class KnowledgeFileParserTest {

	@Test
	void readsTitleAndUrlFromFrontmatter() {
		KnowledgeFile file = KnowledgeFileParser.parse("about.md", """
				---
				title: About Saq
				url: /#about
				---

				# About

				Body text.
				""");

		assertThat(file.path()).isEqualTo("about.md");
		assertThat(file.title()).isEqualTo("About Saq");
		assertThat(file.url()).isEqualTo("/#about");
		assertThat(file.isCitable()).isTrue();
		assertThat(file.body()).startsWith("# About").contains("Body text.");
	}

	@Test
	void stripsTheFrontmatterFromTheBody() {
		KnowledgeFile file = KnowledgeFileParser.parse("about.md", """
				---
				title: About Saq
				url: /#about
				---

				Body text.
				""");

		// The frontmatter is metadata about the file, not knowledge about the owner. Leaving it
		// in the body would put "title:" and "url:" into the embedding and into the prompt.
		assertThat(file.body()).isEqualTo("Body text.");
	}

	@Test
	void unquotesFrontmatterValues() {
		KnowledgeFile file = KnowledgeFileParser.parse("a.md", """
				---
				title: "githMind: a case study"
				url: '/projects/githmind'
				---
				Body.
				""");

		assertThat(file.title()).isEqualTo("githMind: a case study");
		assertThat(file.url()).isEqualTo("/projects/githmind");
	}

	@Test
	void fallsBackToTheFirstHeadingWhenThereIsNoFrontmatter() {
		KnowledgeFile file = KnowledgeFileParser.parse("notes.md", """
				## Some notes

				Body text.
				""");

		assertThat(file.title()).isEqualTo("Some notes");
		assertThat(file.url()).isEmpty();
		// Not citable, but still ingested: the content can ground an answer even when there is
		// no page to link the visitor to.
		assertThat(file.isCitable()).isFalse();
		assertThat(file.body()).startsWith("## Some notes");
	}

	@Test
	void fallsBackToTheFileNameWhenThereIsNoHeadingEither() {
		KnowledgeFile file = KnowledgeFileParser.parse("raw-notes.md", "Just a paragraph.\n");

		assertThat(file.title()).isEqualTo("raw-notes");
	}

	@Test
	void treatsAnUnclosedFenceAsBodyRatherThanFailing() {
		// A half-written knowledge file must not take the chatbot down. The fence never closes,
		// so there is no frontmatter to read and the whole thing is content.
		KnowledgeFile file = KnowledgeFileParser.parse("broken.md", """
				---
				title: Half written
				""");

		assertThat(file.url()).isEmpty();
		assertThat(file.title()).isEqualTo("broken");
		assertThat(file.body()).contains("title: Half written");
	}

	@Test
	void hashesToSixtyFourHexCharacters() {
		KnowledgeFile file = KnowledgeFileParser.parse("a.md", "content");

		// knowledge_source.content_hash is char(64); anything else would be truncated or rejected.
		assertThat(file.contentHash()).hasSize(64).matches("[0-9a-f]+");
	}

	@Test
	void hashesTheSameContentToTheSameValue() {
		assertThat(KnowledgeFileParser.parse("a.md", "content").contentHash())
				.isEqualTo(KnowledgeFileParser.parse("a.md", "content").contentHash());
	}

	@Test
	void hashIgnoresLineEndings() {
		// Otherwise a Windows checkout re-embeds every file that a Linux checkout had already
		// ingested, and the "restarting does not re-embed" guarantee holds only on one OS.
		assertThat(KnowledgeFileParser.parse("a.md", "one\r\ntwo\r\n").contentHash())
				.isEqualTo(KnowledgeFileParser.parse("a.md", "one\ntwo\n").contentHash());
	}

	@Test
	void hashChangesWhenOnlyTheFrontmatterChanges() {
		String before = "---\ntitle: One\nurl: /a\n---\nBody.\n";
		String after = "---\ntitle: Two\nurl: /a\n---\nBody.\n";

		// The title is part of what gets embedded and part of what a citation shows, so editing
		// it has to trigger a re-ingestion even though the body is untouched.
		assertThat(KnowledgeFileParser.parse("a.md", before).contentHash())
				.isNotEqualTo(KnowledgeFileParser.parse("a.md", after).contentHash());
	}

}
