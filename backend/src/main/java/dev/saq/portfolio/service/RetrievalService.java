package dev.saq.portfolio.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.saq.portfolio.config.ChatProperties;
import dev.saq.portfolio.dto.internal.RetrievedContext;
import dev.saq.portfolio.dto.response.ChatSourceResponse;
import dev.saq.portfolio.mapper.ChatMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Finds the parts of the site that might answer a question.
 *
 * <p>One similarity search, bounded by {@code app.chat.top-k} and floored by
 * {@code app.chat.similarity-threshold}. The floor is the feature's honesty mechanism and the
 * most consequential number in {@code app.chat}: a question the site has nothing to say about
 * must come back empty, so that the model is handed no context and admits it. Lower the
 * threshold and the nearest-but-irrelevant chunk becomes the basis of a confident wrong answer
 * about someone's career.
 *
 * <p>Returning nothing is therefore a success, not a failure, and nothing here treats it as an
 * error.
 */
@Service
public class RetrievalService {

	/**
	 * Most chips to show under one answer.
	 *
	 * <p>Three because the panel is 380px wide and a fourth chip wraps to a second row that
	 * pushes the answer out of view, and because an answer supported by more than three pages is
	 * usually a sign the question was vague rather than that all five are worth reading.
	 */
	private static final int MAX_SOURCES = 3;

	/**
	 * How close to the best match a chunk has to score to be cited: within 4%.
	 *
	 * <p>Chosen from the measured spread rather than taste. For "what school he blong to" the
	 * two about.md chunks scored 0.521 and 0.503 while the next page trailed at 0.47 — so a 4%
	 * band keeps the page the answer actually came from and drops the one that merely shares the
	 * corpus's vocabulary.
	 */
	private static final double CITATION_SCORE_RATIO = 0.96;

	private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

	private final VectorStore vectorStore;

	private final ChatMapper chatMapper;

	private final ChatProperties properties;

	public RetrievalService(VectorStore vectorStore, ChatMapper chatMapper, ChatProperties properties) {
		this.vectorStore = vectorStore;
		this.chatMapper = chatMapper;
		this.properties = properties;
	}

	/**
	 * The chunks and citations for one question.
	 *
	 * <p>Only the question is embedded, never the conversation. Folding earlier turns into the
	 * search text sounds helpful and is not: a visitor who asks about githMind and then about the
	 * résumé would keep retrieving githMind, because the older, longer text dominates the vector.
	 *
	 * @param question the visitor's latest message
	 * @return what was found, possibly {@link RetrievedContext#empty()}
	 */
	public RetrievedContext retrieve(String question) {
		SearchRequest request = SearchRequest.builder()
				.query(question)
				.topK(properties.topK())
				.similarityThreshold(properties.similarityThreshold())
				.build();

		List<Document> chunks = vectorStore.similaritySearch(request);
		if (chunks == null || chunks.isEmpty()) {
			log.debug("Retrieval found nothing above the similarity threshold");
			return RetrievedContext.empty();
		}

		return new RetrievedContext(texts(chunks), sources(chunks));
	}

	/** Chunk texts in rank order, with the whole block capped at {@code maxContextChars}. */
	private List<String> texts(List<Document> chunks) {
		List<String> texts = new ArrayList<>(chunks.size());
		int budget = properties.maxContextChars();

		for (Document chunk : chunks) {
			String text = chunk.getText();
			if (text == null || text.isBlank()) {
				continue;
			}
			if (text.length() > budget) {
				// Dropped whole rather than truncated. A chunk cut mid-sentence is the kind of
				// context a model finishes by guessing, and the chunks are rank-ordered, so what
				// is lost here is the least relevant of them.
				log.debug("Context budget exhausted; dropping the remaining {} chunk(s)",
						chunks.size() - texts.size());
				break;
			}
			texts.add(text);
			budget -= text.length();
		}
		return texts;
	}

	/**
	 * Citations, one per page rather than one per chunk, and only the ones worth showing.
	 *
	 * <p>A long case study often contributes three chunks to the same answer, and showing the
	 * same link three times reads as a bug. The first chunk of a page wins, so the snippet shown
	 * belongs to that page's best match, and {@link LinkedHashMap} keeps the chips in the order
	 * relevance put them.
	 *
	 * <p>Chunks whose knowledge file declared no url are skipped: they can ground an answer, but
	 * there is no page to send the visitor to, and a chip that goes nowhere is worse than no chip.
	 *
	 * <p>Retrieval is deliberately broader than citation. Every chunk above the threshold is
	 * worth putting in front of the model, because a weak match still supplies context; but a
	 * citation is a claim that the answer came from that page, and "what school did he go to"
	 * answered from about.md should not also offer the githMind case study. So the chips are
	 * narrowed twice: to pages scoring close to the best match, and to a handful at most.
	 */
	private List<ChatSourceResponse> sources(List<Document> chunks) {
		double best = chunks.stream()
				.map(Document::getScore)
				.filter(score -> score != null)
				.mapToDouble(Double::doubleValue)
				.max()
				.orElse(0);

		Map<String, ChatSourceResponse> byUrl = new LinkedHashMap<>();
		for (Document chunk : chunks) {
			String url = chatMapper.urlOf(chunk);
			if (url.isBlank() || !isWorthCiting(chunk, best)) {
				continue;
			}
			byUrl.putIfAbsent(url, chatMapper.toSource(chunk));
			if (byUrl.size() == MAX_SOURCES) {
				break;
			}
		}
		return List.copyOf(byUrl.values());
	}

	/**
	 * Whether a chunk is close enough to the best match to be named as a source.
	 *
	 * <p>Relative to the best score rather than an absolute floor, because the absolute numbers
	 * move with the embedding model and the question's phrasing — the band for a well-aimed
	 * question on this corpus is around 0.52, and for a vague one around 0.47, so no fixed value
	 * separates "supporting" from "incidental" in both cases. The distance from the best match
	 * does: a page genuinely about the question lands within a few percent of the top chunk,
	 * while a page that merely shares vocabulary trails it.
	 */
	private static boolean isWorthCiting(Document chunk, double best) {
		Double score = chunk.getScore();
		if (score == null || best <= 0) {
			// No score to judge by: cite it rather than silently dropping a real source.
			return true;
		}
		return score >= best * CITATION_SCORE_RATIO;
	}

}
