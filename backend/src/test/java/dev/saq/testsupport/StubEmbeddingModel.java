package dev.saq.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * An {@link EmbeddingModel} that embeds text with a hash instead of a neural network.
 *
 * <p>Exists so the test suite can exercise the real {@code PgVectorStore} — real inserts, real
 * HNSW index, real cosine search, real metadata deletes — without a Gemini API key and without a
 * network call. The vectors mean nothing semantically; what they are is <em>deterministic</em>,
 * so the same text always lands in the same place and a test can assert on which chunk came back.
 *
 * <p>Two properties are deliberate rather than incidental:
 * <ul>
 * <li><b>768 dimensions</b>, matching {@code vector(768)} in {@code V5__knowledge.sql}. A
 * different width would be rejected by Postgres, which is the point: the schema and the model
 * have to agree in tests exactly as they do in production.</li>
 * <li><b>Every component is non-negative</b>, so the cosine distance between any two vectors is
 * at most 1. {@code PgVectorStore} translates a similarity threshold of 0 into
 * {@code WHERE distance < 1}, so vectors allowed to point in opposing directions could be
 * filtered out by a threshold that is supposed to filter nothing — and a retrieval test would
 * fail for a reason that has nothing to do with retrieval.</li>
 * </ul>
 *
 * <p>Similarity here is "shares byte patterns", which is enough for the assertions the suite
 * actually makes: that a query matching one fixture's unusual word ranks that fixture's chunk
 * first, that topK is honoured, and that deleting one source leaves the other's chunks alone.
 * It is not a stand-in for evaluating answer quality; that is what {@code chat-eval.md} is for.
 */
public class StubEmbeddingModel implements EmbeddingModel {

	/** Matches {@code spring.ai.google.genai.embedding.text.dimensions} and the migration. */
	public static final int DIMENSIONS = 768;

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<Embedding> embeddings = new ArrayList<>(request.getInstructions().size());
		for (int i = 0; i < request.getInstructions().size(); i++) {
			embeddings.add(new Embedding(vectorFor(request.getInstructions().get(i)), i));
		}
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public float[] embed(Document document) {
		return vectorFor(document.getText() == null ? "" : document.getText());
	}

	@Override
	public int dimensions() {
		return DIMENSIONS;
	}

	/**
	 * A unit-length, non-negative vector derived from the text's character trigrams.
	 *
	 * <p>Trigrams rather than whole words so that a question and the chunk that answers it
	 * overlap on shared substrings, which is what makes the stub rank anything at all. Normalised
	 * because cosine distance is scale-free but pgvector still has to compute it; an un-normalised
	 * zero vector would produce NaN distances and surprising failures on empty text.
	 */
	static float[] vectorFor(String text) {
		float[] vector = new float[DIMENSIONS];
		byte[] bytes = text.toLowerCase().getBytes(StandardCharsets.UTF_8);

		// A constant floor keeps the vector non-zero for empty input, so normalising is always
		// defined and an empty document is simply equidistant from everything.
		for (int i = 0; i < DIMENSIONS; i++) {
			vector[i] = 0.01f;
		}

		for (int i = 0; i + 2 < bytes.length; i++) {
			int trigram = (bytes[i] & 0xFF) * 31 * 31 + (bytes[i + 1] & 0xFF) * 31 + (bytes[i + 2] & 0xFF);
			vector[Math.abs(trigram) % DIMENSIONS] += 1.0f;
		}

		double length = 0;
		for (float component : vector) {
			length += (double) component * component;
		}
		length = Math.sqrt(length);
		for (int i = 0; i < DIMENSIONS; i++) {
			vector[i] = (float) (vector[i] / length);
		}
		return vector;
	}

}
