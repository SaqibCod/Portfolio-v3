package dev.saq.portfolio.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One markdown file in the chatbot's knowledge base, as it was last ingested.
 *
 * <p>This row is a receipt, not the content: the chunks and their embeddings live in
 * {@code vector_store}, and the only thing kept here is enough to answer "has this file changed
 * since we paid to embed it?". That question is the whole reason the table exists — embedding is
 * a billed API call, and a restart must not re-run it for files nobody touched.
 *
 * <p>The id is the classpath-relative path rather than a surrogate key, because the file *is* the
 * identity: the same path means the same knowledge file, and every chunk carries that path in its
 * {@code source} metadata so re-ingesting one file can delete exactly its own chunks.
 *
 * @param path        classpath-relative file name, e.g. {@code about.md}
 * @param contentHash hex-encoded SHA-256 of the file's bytes, 64 characters
 * @param ingestedAt  when the chunks currently in {@code vector_store} were written
 */
@Table("knowledge_source")
public record KnowledgeSource(
		@Id String path,
		String contentHash,
		Instant ingestedAt) {

}
