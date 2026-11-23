package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.repository.StatusCheckRepository;
import dev.saq.portfolio.scheduler.HealthProbeScheduler;
import dev.saq.portfolio.scheduler.StatusRetentionScheduler;
import dev.saq.portfolio.service.HealthProbeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The status endpoints with nothing monitored — which is what {@code application.yml} ships, and
 * what the site reads to decide whether to publish a status page at all.
 *
 * <p>This is the more important of the two integration tests. An empty service list has to be a
 * working, boring 200 rather than a 500 waiting for its first request: both multi-service queries
 * bind {@code in (:slugs)}, which is a syntax error on an empty collection, so the whole feature
 * rests on {@code StatusService} short-circuiting before it reaches them. A real container and a
 * real Postgres are what make that provable rather than asserted.
 *
 * <p>Both scheduled jobs are driven by hand because they are profile-disabled here. The schedule
 * itself is not what is under test; running against no services without raising anything is.
 *
 * @see StatusMonitoredIntegrationTests for the same endpoints with services configured
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusIntegrationTests {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String STATUS_PATH = "/api/v1/status";

	@LocalServerPort
	private int port;

	@Autowired
	private StatusProperties statusProperties;

	@Autowired
	private HealthProbeService healthProbeService;

	@Autowired
	private StatusCheckRepository repository;

	private RestTestClient client;

	@BeforeEach
	void setUp() {
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void theApplicationStartsWithAnEmptyServiceList() {
		assertThat(statusProperties.services()).isEmpty();
		assertThat(statusProperties.hasNoServices()).isTrue();
	}

	@Test
	void servesAnEmptyOverviewRatherThanFailing() {
		client.get().uri(STATUS_PATH)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectHeader().valueEquals("Cache-Control", "max-age=60, public")
				.expectBody()
				.jsonPath("$.overall").isEqualTo("unknown")
				.jsonPath("$.services.length()").isEqualTo(0)
				.jsonPath("$.checkedAt").exists();
	}

	@Test
	void answersFourOhFourForAnyHistoryRequest() {
		client.get().uri(STATUS_PATH + "/githmind/history")
				.exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "not-found")
				.jsonPath("$.status").isEqualTo(404);
	}

	@Test
	void aProbeSweepOverNoServicesCompletesQuietlyAndWritesNothing() {
		HealthProbeScheduler scheduler =
				new HealthProbeScheduler(statusProperties, healthProbeService);

		assertThatCode(scheduler::probeAll).doesNotThrowAnyException();

		assertThat(repository.count()).isZero();
		client.get().uri(STATUS_PATH).exchange().expectStatus().isOk();
	}

	@Test
	void theRetentionSweepRunsAgainstTheRealSchema() {
		// The delete is plain SQL that no unit test can validate; this is the only place the
		// statement itself meets Postgres.
		StatusRetentionScheduler scheduler =
				new StatusRetentionScheduler(statusProperties, repository);

		assertThatCode(scheduler::deleteExpiredChecks).doesNotThrowAnyException();
	}

}
