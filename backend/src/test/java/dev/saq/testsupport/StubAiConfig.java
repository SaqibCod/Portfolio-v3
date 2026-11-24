package dev.saq.testsupport;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The AI beans the test context runs on. Not one of them talks to a provider.
 *
 * <p>{@code application-test.yml} sets every {@code spring.ai.model.*} selector to {@code none},
 * so no Gemini client and no OpenAI client is auto-configured at all — there is nothing in the
 * context that could reach a network, which is what makes "tests never call a real LLM" a
 * property of the wiring rather than a rule people have to remember. These beans fill the holes
 * that leaves, under the same names the auto-configurations would have used, so production code
 * resolves its dependencies by qualifier exactly as it does when deployed.
 *
 * <p>Imported from {@link dev.saq.portfolio.TestcontainersConfig} rather than per test: every
 * {@code @SpringBootTest} needs the embedding model, because the pgvector auto-configuration
 * cannot build a {@code VectorStore} without one.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubAiConfig {

	/**
	 * Stands in for Gemini's text embedding model.
	 *
	 * <p>The bean name matches {@code GoogleGenAiTextEmbeddingAutoConfiguration}'s, so the
	 * pgvector auto-configuration picks it up the same way it picks up the real one.
	 */
	@Bean
	EmbeddingModel googleGenAiTextEmbedding() {
		return new StubEmbeddingModel();
	}

}
