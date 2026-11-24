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
	 * Citations, one per page rather than one per chunk.
	 *
	 * <p>A long case study often contributes three chunks to the same answer, and showing the
	 * same link three times reads as a bug. The first chunk of a page wins, so the snippet shown
	 * belongs to that page's best match, and {@link LinkedHashMap} keeps the chips in the order
	 * relevance put them.
	 *
	 * <p>Chunks whose knowledge file declared no url are skipped: they can ground an answer, but
	 * there is no page to send the visitor to, and a chip that goes nowhere is worse than no chip.
	 */
	private List<ChatSourceResponse> sources(List<Document> chunks) {
		Map<String, ChatSourceResponse> byUrl = new LinkedHashMap<>();
		for (Document chunk : chunks) {
			String url = chatMapper.urlOf(chunk);
			if (url.isBlank()) {
				continue;
			}
			byUrl.putIfAbsent(url, chatMapper.toSource(chunk));
		}
		return List.copyOf(byUrl.values());
	}

}
