package dev.saq.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusCheck;
import dev.saq.portfolio.model.StatusWindow;
import dev.saq.portfolio.repository.StatusCheckRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Both status endpoints end to end with two services configured: a real servlet container, a real
 * Postgres, and the status codes, JSON shapes and headers the contract promises.
 *
 * <p>Nothing is mocked. The probes themselves never run — the sweep is profile-disabled under
 * {@code test} — so history is seeded through the repository, which writes exactly what a probe
 * would have written. That keeps the test off the network while still exercising the real
 * {@code date_bin} aggregation underneath every assertion about buckets.
 *
 * @see StatusIntegrationTests for the same endpoints with nothing monitored
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"app.status.services[0].slug=githmind",
		"app.status.services[0].name=githMind",
		"app.status.services[0].url=https://githmind.example.com",
		"app.status.services[1].slug=medi-scan",
		"app.status.services[1].name=Medi-Scan",
		"app.status.services[1].url=https://medi-scan.example.com" })
class StatusMonitoredIntegrationTests {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String STATUS_PATH = "/api/v1/status";

	@LocalServerPort
	private int port;

	@Autowired
	private StatusCheckRepository repository;

	private RestTestClient client;

	@BeforeEach
	void setUp() {
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
		repository.deleteAll();
	}

	@Test
	void servesEveryConfiguredServiceEvenBeforeItHasBeenProbed() {
		// A project is on the page because it is configured, so a freshly deployed one shows up
		// as `unknown` from the first request rather than being missing until the first sweep.
		client.get().uri(STATUS_PATH)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.overall").isEqualTo("unknown")
				.jsonPath("$.services.length()").isEqualTo(2)
				.jsonPath("$.services[0].slug").isEqualTo("githmind")
				.jsonPath("$.services[0].name").isEqualTo("githMind")
				.jsonPath("$.services[0].url").isEqualTo("https://githmind.example.com")
				.jsonPath("$.services[0].state").isEqualTo("unknown")
				.jsonPath("$.services[0].uptime30d").isEqualTo(0)
				.jsonPath("$.services[1].slug").isEqualTo("medi-scan");
	}

	@Test
	void reflectsStoredChecksInTheOverview() {
		Instant now = Instant.now();
		repository.saveAll(List.of(
				StatusCheck.record("githmind", now.minusSeconds(300), ServiceState.OPERATIONAL, 182),
				StatusCheck.record("githmind", now, ServiceState.OPERATIONAL, 190),
				StatusCheck.record("medi-scan", now, ServiceState.DOWN, null)));

		client.get().uri(STATUS_PATH)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				// The worst state wins the banner; the latest check wins each row.
				.jsonPath("$.overall").isEqualTo("down")
				.jsonPath("$.services[0].state").isEqualTo("operational")
				.jsonPath("$.services[0].responseTimeMs").isEqualTo(190)
				.jsonPath("$.services[0].uptime30d").isEqualTo(100.0)
				.jsonPath("$.services[1].state").isEqualTo("down")
				.jsonPath("$.services[1].uptime30d").isEqualTo(0);
	}

	@Test
	void servesTheDocumentedBucketCountForEveryWindow() {
		repository.save(StatusCheck.record("githmind", Instant.now(),
				ServiceState.OPERATIONAL, 182));

		expectBucketCount(StatusWindow.LAST_24H, 288);
		expectBucketCount(StatusWindow.LAST_7D, 168);
		expectBucketCount(StatusWindow.LAST_30D, 30);
	}

	@Test
	void servesHistoryInTheContractsShape() {
		Instant now = Instant.now();
		repository.saveAll(List.of(
				StatusCheck.record("githmind", now, ServiceState.OPERATIONAL, 180),
				StatusCheck.record("githmind", now, ServiceState.DEGRADED, 2400)));

		client.get().uri(STATUS_PATH + "/githmind/history?window=30d")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals("Cache-Control", "max-age=60, public")
				.expectBody()
				.jsonPath("$.slug").isEqualTo("githmind")
				.jsonPath("$.window").isEqualTo("30d")
				.jsonPath("$.uptime").isEqualTo(100.0)
				.jsonPath("$.points.length()").isEqualTo(30)
				// Today is the last bucket and holds both checks: degraded is the worse of the
				// two, and the average covers both measured response times.
				.jsonPath("$.points[29].state").isEqualTo("degraded")
				.jsonPath("$.points[29].avgResponseTimeMs").isEqualTo(1290)
				// Everything before today was never probed, which is a point rather than a gap.
				.jsonPath("$.points[0].state").isEqualTo("unknown");
	}

	@Test
	void countsADownCheckAgainstUptime() {
		Instant now = Instant.now();
		repository.saveAll(List.of(
				StatusCheck.record("githmind", now, ServiceState.OPERATIONAL, 180),
				StatusCheck.record("githmind", now, ServiceState.DOWN, null),
				StatusCheck.record("githmind", now, ServiceState.DOWN, null),
				StatusCheck.record("githmind", now, ServiceState.OPERATIONAL, 190)));

		client.get().uri(STATUS_PATH + "/githmind/history?window=30d")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.uptime").isEqualTo(50.0);
	}

	@Test
	void keepsHistoryOutsideTheWindowOutOfTheAnswer() {
		repository.saveAll(List.of(
				StatusCheck.record("githmind", Instant.now().minus(Duration.ofDays(40)),
						ServiceState.DOWN, null),
				StatusCheck.record("githmind", Instant.now(), ServiceState.OPERATIONAL, 182)));

		client.get().uri(STATUS_PATH + "/githmind/history?window=30d")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.uptime").isEqualTo(100.0);
	}

	@Test
	void rejectsAWindowTheContractDoesNotOffer() {
		client.get().uri(STATUS_PATH + "/githmind/history?window=90d")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation");
	}

	@Test
	void answersFourOhFourForASlugThatIsNotConfigured() {
		client.get().uri(STATUS_PATH + "/not-monitored/history")
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "not-found");
	}

	private void expectBucketCount(StatusWindow window, int expected) {
		client.get().uri(STATUS_PATH + "/githmind/history?window=" + window.toApi())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.points.length()").isEqualTo(expected);
	}

}
