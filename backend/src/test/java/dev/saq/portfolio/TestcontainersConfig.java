package dev.saq.portfolio;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers setup. The container runs the same image as local development
 * (Postgres 18 with pgvector), and {@code @ServiceConnection} wires the DataSource to it.
 */
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
