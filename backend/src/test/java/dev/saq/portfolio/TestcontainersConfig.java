package dev.saq.portfolio;

import dev.saq.testsupport.StubAiConfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers setup. The container runs the same image as local development
 * (Postgres 18 with pgvector), and {@code @ServiceConnection} wires the DataSource to it.
 *
 * <p>{@link StubAiConfig} rides along because the two are inseparable in practice: once the
 * pgvector vector store is on the classpath, no {@code @SpringBootTest} can build a context
 * without an {@code EmbeddingModel}, and the one thing a test must never use is a real one.
 * Importing it here means every integration test gets the safe wiring by default instead of
 * discovering the requirement through a startup failure.
 */
@Import(StubAiConfig.class)
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName
			.parse("pgvector/pgvector:pg18")
			.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE);
	}

}
