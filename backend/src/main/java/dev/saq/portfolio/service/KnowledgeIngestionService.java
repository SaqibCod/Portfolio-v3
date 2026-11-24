package dev.saq.portfolio.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.saq.portfolio.config.ChatProperties;
import dev.saq.portfolio.dto.internal.KnowledgeFile;
import dev.saq.portfolio.model.KnowledgeSource;
import dev.saq.portfolio.repository.KnowledgeSourceRepository;
import dev.saq.portfolio.util.KnowledgeFileParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

/**
 * Keeps {@code vector_store} in step with the markdown files under {@code resources/knowledge}.
 *
 * <p>Runs once per start, after the application is ready, and is a no-op whenever nothing has
 * changed. That last part is the whole design: embedding is a billed API call against a free
 * tier, so the unit of work is "files whose SHA-256 differs from the receipt in
 * {@code knowledge_source}", not "every file". Restarting the backend twice in a row costs one
 * table scan and zero tokens.
 *
 * <p>Three cases, and each is a complete unit:
 * <ul>
 * <li><b>New or changed</b> — delete the file's existing chunks by their {@code source}
 * metadata, split, embed, insert, then write the receipt. The delete comes first and is scoped
 * by path, so a file that shrank from nine chunks to four leaves no orphans behind.</li>
 * <li><b>Unchanged</b> — nothing at all, not even a write to {@code ingested_at}.</li>
 * <li><b>Gone from the repository</b> — chunks and receipt both go, so the chatbot stops citing
 * a page that no longer exists.</li>
 * </ul>
 *
 * <p>Each file's work runs in its own transaction through {@link TransactionTemplate} rather than
 * an {@code @Transactional} method: the entry point is an event listener calling into the same
 * bean, where a proxy-based annotation would be silently ignored. Per-file rather than per-run
 * means a provider failure on the fourth file leaves the first three ingested and the fourth
 * untouched, and the next start retries exactly what is missing — a receipt is only ever written
 * after the chunks it describes.
 *
 * <p>A failure never stops startup. The site is a portfolio: an unavailable chatbot is a degraded
 * widget, while a backend that refuses to boot takes the guestbook, the status page and the
 * contact form down with it.
 */
@Service
public class KnowledgeIngestionService {

	/** Chunk metadata key holding the file a chunk came from; the handle every delete uses. */
	public static final String SOURCE_METADATA = "source";

	/** Chunk metadata key holding the citation's display name. */
	public static final String TITLE_METADATA = "title";

	/** Chunk metadata key holding the page a citation links to. */
	public static final String URL_METADATA = "url";

	private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

	/**
	 * Roughly 400 tokens per chunk.
	 *
	 * <p>Half the splitter's 800-token default, because these files are short and heterogeneous —
	 * a resume section and an architecture note should not share a chunk, or a question about one
	 * retrieves the other.
	 *
	 * <p>No chunk overlap: {@code withChunkOverlap} does not exist in Spring AI 2.0.x (it arrives
	 * in 2.1), so consecutive chunks here share no text. The splitter still breaks at the
	 * configured punctuation marks once past {@code minChunkSizeChars}, so a boundary lands
	 * between sentences rather than inside one; what is lost is only the surrounding paragraph,
	 * which the prepended title partly makes up for. Worth revisiting on the 2.1 upgrade.
	 */
	private static final TokenTextSplitter SPLITTER = TokenTextSplitter.builder()
			.withChunkSize(400)
			.withMinChunkSizeChars(200)
			.build();

	private final ChatProperties properties;

	private final ResourcePatternResolver resourceResolver;

	private final VectorStore vectorStore;

	private final KnowledgeSourceRepository repository;

	private final TransactionTemplate transactionTemplate;

	/**
	 * Takes a {@link ResourceLoader} rather than a {@link ResourcePatternResolver}: the former is
	 * a dependency the application context resolves for you, the latter is not a bean. Wrapping it
	 * here keeps {@code classpath:knowledge/*.md} resolving against the same loader the rest of the
	 * application reads resources with.
	 */
	public KnowledgeIngestionService(ChatProperties properties, ResourceLoader resourceLoader,
			VectorStore vectorStore, KnowledgeSourceRepository repository,
			TransactionTemplate transactionTemplate) {

		this.properties = properties;
		this.resourceResolver = new PathMatchingResourcePatternResolver(resourceLoader);
		this.vectorStore = vectorStore;
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * Ingests on startup, unless {@code app.chat.ingest-on-startup} says otherwise.
	 *
	 * <p>{@code ApplicationReadyEvent} rather than {@code @PostConstruct}: the embedding provider
	 * is an external call, and nothing should stand between the context being built and the port
	 * being open.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void ingestOnStartup() {
		if (!properties.ingestOnStartup()) {
			log.info("Knowledge ingestion on startup is disabled; the vector store is left as it is");
			return;
		}
		try {
			ingest();
		}
		catch (RuntimeException ex) {
			// Deliberately swallowed; see the class javadoc. The chatbot answers from whatever is
			// already stored, or 503s, and every other feature is unaffected.
			log.error("Knowledge ingestion failed; the chatbot will answer from whatever is already "
					+ "in the vector store", ex);
		}
	}

	/**
	 * Reconciles the knowledge files on the classpath with what has been ingested.
	 *
	 * @return how many files were embedded this run; zero means every file was unchanged, which
	 *         is what a restart with no content changes must cost
	 */
	public int ingest() {
		Map<String, KnowledgeFile> files = readKnowledgeFiles();

		Map<String, String> ingested = new HashMap<>();
		for (KnowledgeSource source : repository.findAll()) {
			ingested.put(source.path(), source.contentHash());
		}

		int embedded = 0;
		for (KnowledgeFile file : files.values()) {
			if (file.contentHash().equals(ingested.get(file.path()))) {
				log.debug("Knowledge file {} is unchanged; not re-embedding", file.path());
				continue;
			}
			transactionTemplate.executeWithoutResult(status -> replaceChunks(file));
			embedded++;
			log.info("Embedded knowledge file {}", file.path());
		}

		for (String path : ingested.keySet()) {
			if (!files.containsKey(path)) {
				transactionTemplate.executeWithoutResult(status -> forget(path));
				log.info("Knowledge file {} is gone; removed its chunks", path);
			}
		}

		log.info("Knowledge ingestion done: {} file(s) on the classpath, {} embedded this run",
				files.size(), embedded);
		return embedded;
	}

	/** Drops a file's existing chunks, embeds its current content, and writes the receipt. */
	private void replaceChunks(KnowledgeFile file) {
		deleteChunks(file.path());

		List<Document> chunks = chunk(file);
		if (chunks.isEmpty()) {
			// An empty file is a valid state: a placeholder nobody has written yet. The hash is
			// still recorded, so it is not re-examined on every start.
			log.warn("Knowledge file {} produced no chunks; nothing to embed", file.path());
		}
		else {
			vectorStore.add(chunks);
		}

		repository.upsert(file.path(), file.contentHash(), Instant.now());
	}

	/** Drops a file's chunks and its receipt. */
	private void forget(String path) {
		deleteChunks(path);
		repository.deleteByPath(path);
	}

	/**
	 * Deletes every chunk whose {@code source} is this path.
	 *
	 * <p>A metadata filter rather than a list of ids, because chunk ids are generated at
	 * ingestion time and nothing records them — the path is the only durable handle on "what this
	 * file contributed".
	 */
	private void deleteChunks(String path) {
		FilterExpressionBuilder filter = new FilterExpressionBuilder();
		vectorStore.delete(filter.eq(SOURCE_METADATA, path).build());
	}

	/**
	 * Splits one file into chunks, each carrying its citation metadata and its own title.
	 *
	 * <p>The title is prepended to every chunk's text, not only the first. A chunk is retrieved
	 * on its own and lands in the prompt on its own, so without this the model sees a paragraph
	 * about a circuit breaker with no indication of which project it belongs to.
	 */
	private List<Document> chunk(KnowledgeFile file) {
		if (file.body().isBlank()) {
			return List.of();
		}

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(SOURCE_METADATA, file.path());
		metadata.put(TITLE_METADATA, file.title());
		metadata.put(URL_METADATA, file.url());

		List<Document> split = SPLITTER.apply(List.of(new Document(file.body(), metadata)));
		List<Document> titled = new ArrayList<>(split.size());
		for (Document chunk : split) {
			titled.add(new Document(file.title() + "\n\n" + chunk.getText(), metadata));
		}
		return titled;
	}

	/** Every knowledge file matching {@code app.chat.knowledge-location}, keyed by file name. */
	private Map<String, KnowledgeFile> readKnowledgeFiles() {
		Resource[] resources;
		try {
			resources = resourceResolver.getResources(properties.knowledgeLocation());
		}
		catch (IOException ex) {
			throw new IllegalStateException(
					"Could not list knowledge files at " + properties.knowledgeLocation(), ex);
		}

		Map<String, KnowledgeFile> files = new LinkedHashMap<>();
		for (Resource resource : resources) {
			String path = resource.getFilename();
			if (path == null) {
				continue;
			}
			try (InputStream in = resource.getInputStream()) {
				KnowledgeFile file = KnowledgeFileParser.parse(path,
						StreamUtils.copyToString(in, StandardCharsets.UTF_8));
				if (!file.isCitable()) {
					log.warn("Knowledge file {} declares no frontmatter url; its content will ground "
							+ "answers but it will never be offered as a source", path);
				}
				files.put(path, file);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Could not read knowledge file " + path, ex);
			}
		}
		return files;
	}

}
