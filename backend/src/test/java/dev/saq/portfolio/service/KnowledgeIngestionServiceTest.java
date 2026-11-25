package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.config.ChatProperties;
import dev.saq.portfolio.model.KnowledgeSource;
import dev.saq.portfolio.repository.KnowledgeSourceRepository;
import dev.saq.portfolio.util.KnowledgeFileParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The reconciliation rules: what gets embedded, what does not, and in what order.
 *
 * <p>The vector store is mocked here on purpose. What this class decides is which files need
 * work, and that decision is pure bookkeeping over hashes — testing it against a real database
 * would make the assertions about chunk counts rather than about the rule being asserted.
 * {@link dev.saq.portfolio.KnowledgeIngestionIntegrationTests} covers the other half, where the
 * chunks actually have to land in pgvector and come back out.
 *
 * <p>Fixtures come from {@code src/test/resources/knowledge-test}, read through the real
 * classpath resolver, so the pattern in {@code app.chat.knowledge-location} is exercised too.
 */
class KnowledgeIngestionServiceTest {

	private static final String KNOWLEDGE_PATTERN = "classpath:knowledge-test/*.md";

	/** The three fixtures the pattern matches. */
	private static final List<String> FIXTURES = List.of("alpha.md", "beta.md", "no-frontmatter.md");

	private VectorStore vectorStore;

	private KnowledgeSourceRepository repository;

	private KnowledgeIngestionService service;

	@BeforeEach
	void setUp() {
		vectorStore = mock(VectorStore.class);
		repository = mock(KnowledgeSourceRepository.class);
		when(repository.findAll()).thenReturn(List.of());
		service = newService(true);
	}

	private KnowledgeIngestionService newService(boolean ingestOnStartup) {
		ChatProperties properties = new ChatProperties(5, 0.6, 8000, Duration.ofSeconds(15),
				"classpath:prompts/system.st", KNOWLEDGE_PATTERN, 10, ingestOnStartup, "Saq", "/#contact");

		// A TransactionTemplate with no transaction manager behind it: the callback runs inline,
		// which is all these tests need. What the template guarantees in production (chunks and
		// receipt commit together) is a database property, and so is tested against a database.
		TransactionTemplate transactionTemplate = new TransactionTemplate() {
			@Override
			public <T> T execute(TransactionCallback<T> action) {
				return action.doInTransaction(new SimpleTransactionStatus());
			}
		};

		return new KnowledgeIngestionService(properties, new DefaultResourceLoader(), vectorStore,
				repository, transactionTemplate);
	}

	@Test
	void embedsEveryFileOnAnEmptyStore() {
		assertThat(service.ingest()).isEqualTo(FIXTURES.size());

		verify(repository).upsert(eq("alpha.md"), anyString(), any(Instant.class));
		verify(repository).upsert(eq("beta.md"), anyString(), any(Instant.class));
		verify(repository).upsert(eq("no-frontmatter.md"), anyString(), any(Instant.class));
	}

	@Test
	void ingestsAFileWithNoFrontmatterRatherThanSkippingIt() {
		service.ingest();

		// It cannot be cited, but its content still has to be able to ground an answer -- the
		// alternative is knowledge silently missing because someone forgot a url line.
		verify(repository).upsert(eq("no-frontmatter.md"), anyString(), any(Instant.class));
	}

	@Test
	void embedsNothingWhenEveryHashMatches() {
		when(repository.findAll()).thenReturn(FIXTURES.stream().map(this::receiptFor).toList());

		// The acceptance criterion in the phase file: restarting the backend does not re-embed
		// unchanged files. Embedding is a billed call, so this is the difference between a free
		// restart and paying for the whole knowledge base again.
		assertThat(service.ingest()).isZero();
		verifyNoInteractions(vectorStore);
		verify(repository, never()).upsert(anyString(), anyString(), any(Instant.class));
	}

	@Test
	void doesNotEvenTouchIngestedAtForAnUnchangedFile() {
		when(repository.findAll()).thenReturn(FIXTURES.stream().map(this::receiptFor).toList());

		service.ingest();

		// `ingested_at` means "when the chunks currently stored were written", so refreshing it
		// on a run that wrote nothing would make the column a startup log instead of a receipt.
		verify(repository, never()).upsert(anyString(), anyString(), any(Instant.class));
	}

	@Test
	void reEmbedsOnlyTheFileWhoseHashChanged() {
		when(repository.findAll()).thenReturn(List.of(
				new KnowledgeSource("alpha.md", "0".repeat(64), Instant.EPOCH),
				receiptFor("beta.md"),
				receiptFor("no-frontmatter.md")));

		assertThat(service.ingest()).isEqualTo(1);
		verify(repository).upsert(eq("alpha.md"), anyString(), any(Instant.class));
		verify(repository, never()).upsert(eq("beta.md"), anyString(), any(Instant.class));
	}

	@Test
	void deletesAFilesOldChunksBeforeAddingItsNewOnes() {
		when(repository.findAll()).thenReturn(List.of(
				new KnowledgeSource("alpha.md", "0".repeat(64), Instant.EPOCH),
				receiptFor("beta.md"),
				receiptFor("no-frontmatter.md")));

		service.ingest();

		// Order is the point: a file that shrank from nine chunks to four would otherwise leave
		// five stale chunks behind, and a question could be answered from content that is gone.
		InOrder inOrder = Mockito.inOrder(vectorStore);
		inOrder.verify(vectorStore).delete(any(Filter.Expression.class));
		inOrder.verify(vectorStore).add(any());
	}

	@Test
	void writesTheReceiptOnlyAfterTheChunksAreStored() {
		service.ingest();

		// If the receipt went first, a provider failure mid-run would leave a hash claiming
		// chunks that were never written -- and the next start would believe it and skip them.
		InOrder inOrder = Mockito.inOrder(vectorStore, repository);
		inOrder.verify(vectorStore).add(any());
		inOrder.verify(repository).upsert(anyString(), anyString(), any(Instant.class));
	}

	@Test
	void tagsEveryChunkWithItsSourceTitleAndUrl() {
		service.ingest();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(vectorStore, Mockito.atLeastOnce()).add(captor.capture());

		List<Document> alpha = captor.getAllValues().stream()
				.filter(chunks -> "alpha.md".equals(chunks.getFirst().getMetadata().get("source")))
				.findFirst()
				.orElseThrow();

		assertThat(alpha).isNotEmpty().allSatisfy(chunk -> {
			assertThat(chunk.getMetadata())
					.containsEntry("source", "alpha.md")
					.containsEntry("title", "Alpha case study")
					.containsEntry("url", "/projects/alpha");
			// Every chunk is retrieved and prompted alone, so each one has to name what it is
			// about; a chunk that only says "it is written in Kotlin" grounds nothing.
			assertThat(chunk.getText()).startsWith("Alpha case study");
		});
	}

	@Test
	void forgetsAFileThatIsNoLongerOnTheClasspath() {
		when(repository.findAll()).thenReturn(List.of(
				receiptFor("alpha.md"),
				receiptFor("beta.md"),
				receiptFor("no-frontmatter.md"),
				new KnowledgeSource("deleted.md", "0".repeat(64), Instant.EPOCH)));

		service.ingest();

		verify(vectorStore).delete(any(Filter.Expression.class));
		verify(repository).deleteByPath("deleted.md");
	}

	@Test
	void doesNothingOnStartupWhenIngestionIsDisabled() {
		newService(false).ingestOnStartup();

		verifyNoInteractions(vectorStore, repository);
	}

	@Test
	void doesNotLetAFailedIngestionStopStartup() {
		when(repository.findAll()).thenThrow(new IllegalStateException("database is unreachable"));

		// A portfolio with a broken chatbot is a degraded widget; a portfolio that will not boot
		// has lost its guestbook, its status page and its contact form as well.
		service.ingestOnStartup();
	}

	/** A receipt whose hash matches the fixture currently on the classpath. */
	private KnowledgeSource receiptFor(String path) {
		return new KnowledgeSource(path, hashOf(path), Instant.EPOCH);
	}

	private String hashOf(String path) {
		try (InputStream in = new DefaultResourceLoader()
				.getResource("classpath:knowledge-test/" + path)
				.getInputStream()) {

			String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return KnowledgeFileParser.parse(path, content).contentHash();
		}
		catch (IOException ex) {
			throw new IllegalStateException("fixture " + path + " is missing", ex);
		}
	}

}
