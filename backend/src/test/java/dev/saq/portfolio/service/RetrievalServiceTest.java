package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.saq.portfolio.config.ChatProperties;
import dev.saq.portfolio.dto.internal.RetrievedContext;
import dev.saq.portfolio.mapper.ChatMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * What goes into the prompt, and what the visitor is shown as a source.
 *
 * <p>The vector store is mocked so the search can return exactly the chunks a case needs. Whether
 * similarity search works is Postgres' business and
 * {@link dev.saq.portfolio.KnowledgeIngestionIntegrationTests}'; what matters here is the
 * narrowing this class does on top of it — the budget, the deduplication, and the empty case that
 * the chatbot's honesty depends on.
 */
class RetrievalServiceTest {

	private static final int TOP_K = 5;

	private static final double THRESHOLD = 0.62;

	private final VectorStore vectorStore = mock(VectorStore.class);

	@Test
	void searchesWithTheConfiguredTopKAndThreshold() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of());

		service(8000).retrieve("What does Saq build?");

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		org.mockito.Mockito.verify(vectorStore).similaritySearch(captor.capture());

		// The threshold is the honesty setting: it is what makes an unanswerable question return
		// nothing instead of the nearest irrelevant paragraph.
		assertThat(captor.getValue().getTopK()).isEqualTo(TOP_K);
		assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(THRESHOLD);
		assertThat(captor.getValue().getQuery()).isEqualTo("What does Saq build?");
	}

	@Test
	void searchesOnTheQuestionAlone() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of());

		service(8000).retrieve("Tell me about the résumé");

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		org.mockito.Mockito.verify(vectorStore).similaritySearch(captor.capture());

		// Folding earlier turns into the search text sounds helpful and is not: a visitor who
		// asks about githMind and then about the résumé would keep retrieving githMind, because
		// the longer earlier text dominates the embedding.
		assertThat(captor.getValue().getQuery()).isEqualTo("Tell me about the résumé");
	}

	@Test
	void returnsEmptyWhenNothingClearsTheThreshold() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of());

		RetrievedContext retrieved = service(8000).retrieve("Who won the 1998 World Cup?");

		// Not an error. This is the path that produces "I don't have that on the site", which is
		// the single most important answer the assistant gives.
		assertThat(retrieved.isEmpty()).isTrue();
		assertThat(retrieved.sources()).isEmpty();
	}

	@Test
	void toleratesAStoreThatReturnsNull() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(null);

		// VectorStore#similaritySearch is declared nullable. A null here would otherwise be a 500
		// on the one path that is supposed to degrade gracefully.
		assertThat(service(8000).retrieve("anything").isEmpty()).isTrue();
	}

	@Test
	void keepsChunksInRankOrder() {
		given(vectorStore.similaritySearch(any(SearchRequest.class)))
				.willReturn(List.of(chunk("best match", "/a", "A"), chunk("weaker match", "/b", "B")));

		assertThat(service(8000).retrieve("q").chunks())
				.containsExactly("best match", "weaker match");
	}

	@Test
	void showsOnePageOnceEvenWhenSeveralOfItsChunksMatch() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
				chunk("first part of the case study", "/projects/githmind", "githMind"),
				chunk("second part of the case study", "/projects/githmind", "githMind"),
				chunk("about the owner", "/#about", "About")));

		RetrievedContext retrieved = service(8000).retrieve("q");

		// Both chunks belong in the prompt -- they are both relevant -- but the same link three
		// times under an answer reads as a bug.
		assertThat(retrieved.chunks()).hasSize(3);
		assertThat(retrieved.sources()).extracting("url")
				.containsExactly("/projects/githmind", "/#about");
	}

	@Test
	void citesThePageUsingItsBestMatchingChunk() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
				chunk("the part that actually answered the question", "/projects/githmind", "githMind"),
				chunk("a weaker paragraph from the same page", "/projects/githmind", "githMind")));

		// The snippet should show the visitor why the page was cited, which is the chunk that
		// ranked highest -- not whichever one happened to come last.
		assertThat(service(8000).retrieve("q").sources())
				.singleElement()
				.extracting("snippet")
				.isEqualTo("the part that actually answered the question");
	}

	@Test
	void groundsAnAnswerOnAChunkWithNoUrlWithoutCitingIt() {
		given(vectorStore.similaritySearch(any(SearchRequest.class)))
				.willReturn(List.of(chunk("useful but unlinkable", "", "Notes")));

		RetrievedContext retrieved = service(8000).retrieve("q");

		// A knowledge file with no frontmatter url still holds knowledge. What it cannot have is
		// a chip, because there is no page to send the visitor to.
		assertThat(retrieved.chunks()).containsExactly("useful but unlinkable");
		assertThat(retrieved.sources()).isEmpty();
	}

	@Test
	void doesNotCiteAPageThatOnlySharesVocabulary() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
				scored("the page the answer came from", "/#about", "About", 0.52),
				scored("same page, second chunk", "/#about", "About", 0.50),
				// Above the retrieval threshold, so it is worth showing the model -- but it
				// trails the best match, and citing it would mean telling a visitor asking
				// about school to go and read a project case study.
				scored("a project that shares the corpus's words", "/projects/githmind", "githMind", 0.47)));

		RetrievedContext retrieved = service(8000).retrieve("what school did he go to");

		// All three ground the answer; only the page it came from is offered as a source.
		assertThat(retrieved.chunks()).hasSize(3);
		assertThat(retrieved.sources()).extracting("url").containsExactly("/#about");
	}

	@Test
	void capsTheNumberOfChipsItShows() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
				scored("a", "/a", "A", 0.60),
				scored("b", "/b", "B", 0.60),
				scored("c", "/c", "C", 0.60),
				scored("d", "/d", "D", 0.60),
				scored("e", "/e", "E", 0.60)));

		// A fourth chip wraps to a second row in a 380px panel and pushes the answer out of
		// view. Five equally-good sources also usually means the question was vague.
		assertThat(service(8000).retrieve("q").sources()).hasSize(3);
	}

	@Test
	void citesEveryCloseMatchUpToTheCap() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
				scored("best", "/a", "A", 0.520),
				scored("almost as good", "/b", "B", 0.515)));

		// Two pages genuinely supporting one answer is a real case -- a question about this
		// site is answered from both the tech page and the portfolio case study.
		assertThat(service(8000).retrieve("q").sources())
				.extracting("url")
				.containsExactly("/a", "/b");
	}

	@Test
	void citesAChunkThatCarriesNoScore() {
		// PgVectorStore always scores a result, but a VectorStore is an interface and nothing
		// in the contract promises one. Dropping a real source over a missing number would be
		// the wrong way to fail.
		given(vectorStore.similaritySearch(any(SearchRequest.class)))
				.willReturn(List.of(chunk("content", "/#about", "About")));

		assertThat(service(8000).retrieve("q").sources()).hasSize(1);
	}

	@Test
	void stopsAddingChunksOnceTheContextBudgetIsSpent() {
		given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
				chunk("x".repeat(60), "/a", "A"),
				chunk("y".repeat(60), "/b", "B"),
				chunk("z".repeat(60), "/c", "C")));

		RetrievedContext retrieved = service(100).retrieve("q");

		// The budget bounds what one request can cost. Chunks are rank-ordered, so the ones
		// dropped are the least relevant -- and they are dropped whole rather than truncated,
		// because a chunk cut mid-sentence is context a model completes by guessing.
		assertThat(retrieved.chunks()).hasSize(1);
		assertThat(retrieved.chunks().getFirst()).hasSize(60);
	}

	@Test
	void skipsChunksWithNoText() {
		given(vectorStore.similaritySearch(any(SearchRequest.class)))
				.willReturn(List.of(chunk("", "/a", "A"), chunk("real content", "/b", "B")));

		assertThat(service(8000).retrieve("q").chunks()).containsExactly("real content");
	}

	private RetrievalService service(int maxContextChars) {
		ChatProperties properties = new ChatProperties(TOP_K, THRESHOLD, maxContextChars,
				Duration.ofSeconds(15), "classpath:prompts/system.st",
				"classpath:knowledge-test/*.md", 10, false, "Saq", "/#contact");

		return new RetrievalService(vectorStore, new ChatMapper(), properties);
	}

	private static Document chunk(String text, String url, String title) {
		return new Document(text, Map.of("source", title + ".md", "title", title, "url", url));
	}

	/** A chunk with the similarity score {@code PgVectorStore} would have attached. */
	private static Document scored(String text, String url, String title, double score) {
		return Document.builder()
				.text(text)
				.metadata(Map.of("source", title + ".md", "title", title, "url", url))
				.score(score)
				.build();
	}

}
