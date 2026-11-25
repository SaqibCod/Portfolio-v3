package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.repository.KnowledgeSourceRepository;
import dev.saq.portfolio.service.KnowledgeIngestionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ingestion against a real pgvector database: the schema in {@code V5__knowledge.sql}, the real
 * {@code PgVectorStore}, real inserts, a real HNSW index and real metadata deletes.
 *
 * <p>Worth a container because the two things most likely to break are not logic. The first is
 * the schema contract — {@code PgVectorStore} is configured never to create its own table, so if
 * the migration's column names, the {@code vector(768)} width or the {@code json} metadata type
 * drifted from what the store writes, nothing would complain until the first insert. The second
 * is the delete, which is a JSON-path filter translated into SQL by Spring AI; a mock cannot tell
 * you whether it matches rows.
 *
 * <p>The embedding model is {@link dev.saq.testsupport.StubEmbeddingModel} — deterministic, 768
 * dimensions, no network. Nothing here calls a provider.
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest
class KnowledgeIngestionIntegrationTests {

	/** The fixtures {@code app.chat.knowledge-location} matches under the test profile. */
	private static final List<String> FIXTURES = List.of("alpha.md", "beta.md", "no-frontmatter.md");

	@Autowired
	private KnowledgeIngestionService ingestionService;

	@Autowired
	private KnowledgeSourceRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void reset() {
		// `app.chat.ingest-on-startup` is false under this profile, so the store starts empty and
		// every test below drives ingestion itself.
		jdbcTemplate.update("delete from vector_store");
		jdbcTemplate.update("delete from knowledge_source");
	}

	@Test
	void writesChunksAndReceiptsForEveryKnowledgeFile() {
		assertThat(ingestionService.ingest()).isEqualTo(FIXTURES.size());

		assertThat(repository.findAll())
				.extracting("path")
				.containsExactlyInAnyOrderElementsOf(FIXTURES);
		assertThat(chunkCount()).isPositive();
	}

	@Test
	void storesTheCitationMetadataAlongsideEachChunk() {
		ingestionService.ingest();

		List<String> urls = jdbcTemplate.queryForList(
				"select distinct metadata ->> 'url' as url from vector_store where metadata ->> 'source' = ?",
				String.class, "alpha.md");

		// The url is what SourceChips links to. It travels with the chunk rather than being
		// looked up later, so a citation can only ever name a file that was actually retrieved.
		assertThat(urls).containsExactly("/projects/alpha");
	}

	@Test
	void secondRunEmbedsNothing() {
		ingestionService.ingest();
		long afterFirstRun = chunkCount();

		// The phase's acceptance criterion, proved end to end: hashes match, so no file is
		// re-split, nothing is re-embedded, and the stored chunks are left exactly as they were.
		assertThat(ingestionService.ingest()).isZero();
		assertThat(chunkCount()).isEqualTo(afterFirstRun);
	}

	@Test
	void reIngestingOneFileReplacesOnlyItsOwnChunks() {
		ingestionService.ingest();
		long betaChunks = chunkCount("beta.md");

		// Rewind one receipt's hash: to ingestion this is indistinguishable from someone editing
		// alpha.md, and it is the cheapest way to prove the delete is scoped by source.
		repository.upsert("alpha.md", "0".repeat(64), Instant.EPOCH);

		assertThat(ingestionService.ingest()).isEqualTo(1);
		assertThat(chunkCount("beta.md")).isEqualTo(betaChunks);
		assertThat(chunkCount("alpha.md")).isPositive();
	}

	@Test
	void doesNotAccumulateDuplicateChunksWhenAFileIsReIngested() {
		ingestionService.ingest();
		long alphaChunks = chunkCount("alpha.md");

		repository.upsert("alpha.md", "0".repeat(64), Instant.EPOCH);
		ingestionService.ingest();

		// Chunk ids are generated fresh every run, so without the delete-first step the same
		// content would pile up and the same paragraph would fill the whole prompt.
		assertThat(chunkCount("alpha.md")).isEqualTo(alphaChunks);
	}

	@Test
	void forgettingASourceRemovesItsChunksAndItsReceipt() {
		ingestionService.ingest();

		// A receipt for a file that no longer exists on the classpath, which is what deleting a
		// knowledge file looks like on the next start.
		repository.upsert("gone.md", "0".repeat(64), Instant.EPOCH);
		ingestionService.ingest();

		assertThat(repository.findAll())
				.extracting("path")
				.containsExactlyInAnyOrderElementsOf(FIXTURES);
	}

	private long chunkCount() {
		Long count = jdbcTemplate.queryForObject("select count(*) from vector_store", Long.class);
		return count == null ? 0 : count;
	}

	private long chunkCount(String source) {
		Long count = jdbcTemplate.queryForObject(
				"select count(*) from vector_store where metadata ->> 'source' = ?", Long.class, source);
		return count == null ? 0 : count;
	}

}
