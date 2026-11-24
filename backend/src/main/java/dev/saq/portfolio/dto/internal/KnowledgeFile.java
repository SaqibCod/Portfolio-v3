package dev.saq.portfolio.dto.internal;

/**
 * One markdown knowledge file as read from the classpath: its frontmatter, its body, and the
 * hash that decides whether it needs embedding again.
 *
 * <p>Internal to ingestion and retrieval — never serialised, never part of a response. The
 * public shape of a citation is {@code dto.response.ChatSourceResponse}.
 *
 * @param path        classpath-relative file name, which is also the chunk metadata's
 *                    {@code source} and the {@code knowledge_source} primary key
 * @param title       frontmatter {@code title}, falling back to the first markdown heading and
 *                    then to the file name — a chunk is always attributable to something
 * @param url         frontmatter {@code url}: the page a citation should link to. Blank when the
 *                    file declares none, in which case its chunks still ground an answer but
 *                    never appear as a source
 * @param body        the markdown with the frontmatter block removed
 * @param contentHash hex-encoded SHA-256 of the whole file, frontmatter included — editing only
 *                    a title still counts as a change
 */
public record KnowledgeFile(
		String path,
		String title,
		String url,
		String body,
		String contentHash) {

	/** True when this file declares a page a citation can point at. */
	public boolean isCitable() {
		return !url.isBlank();
	}

}
