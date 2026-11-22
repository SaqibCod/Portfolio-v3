package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.dto.internal.StatusBucketRow;
import dev.saq.portfolio.dto.internal.StatusUptimeRow;
import dev.saq.portfolio.dto.response.StatusHistoryResponse;
import dev.saq.portfolio.dto.response.StatusOverviewResponse;
import dev.saq.portfolio.exception.NotFoundException;
import dev.saq.portfolio.mapper.StatusMapper;
import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusCheck;
import dev.saq.portfolio.model.StatusWindow;
import dev.saq.portfolio.repository.StatusCheckRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The reading side: how configuration and stored checks combine into the two responses.
 *
 * <p>The repository is mocked because every question here is about assembly rather than SQL --
 * which service gets which state, what an absent row means, how the overall banner is chosen.
 * {@code StatusCheckRepositoryTest} covers the queries themselves against a real Postgres.
 */
class StatusServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T10:37:41Z");

	private static final StatusProperties.Service GITHMIND = new StatusProperties.Service(
			"githmind", "githMind", "https://githmind.example.com", "/actuator/health");

	private static final StatusProperties.Service MEDI_SCAN = new StatusProperties.Service(
			"medi-scan", "Medi-Scan", "https://medi-scan.example.com", "/actuator/health");

	private StatusCheckRepository repository;

	@BeforeEach
	void setUp() {
		this.repository = mock(StatusCheckRepository.class);
	}

	@Nested
	class WithNothingMonitored {

		/**
		 * The configuration the site ships in, and the one case that must not reach the
		 * repository: both multi-service queries bind {@code in (:slugs)}, which is a syntax
		 * error on an empty collection.
		 */
		@Test
		void answersFromConfigurationAloneAndNeverTouchesTheDatabase() {
			StatusOverviewResponse overview = service(List.of()).getOverview();

			assertThat(overview.services()).isEmpty();
			assertThat(overview.overall()).isEqualTo("unknown");
			assertThat(overview.checkedAt()).isEqualTo(NOW);
			verifyNoInteractions(repository);
		}

		@Test
		void hasNoHistoryForAnySlug() {
			StatusService service = service(List.of());

			assertThatExceptionOfType(NotFoundException.class)
					.isThrownBy(() -> service.getHistory("githmind", StatusWindow.LAST_24H));
			verifyNoInteractions(repository);
		}

	}

	@Nested
	class Overview {

		@Test
		void reportsAConfiguredServiceThatHasNeverBeenProbedAsUnknown() {
			// A project appears because it is configured, not because it has been probed, so a
			// freshly added one is visible from the first request rather than from the first sweep.
			stubOverview(List.of(), List.of());

			StatusOverviewResponse overview = service(List.of(GITHMIND)).getOverview();

			assertThat(overview.services()).singleElement().satisfies(entry -> {
				assertThat(entry.slug()).isEqualTo("githmind");
				assertThat(entry.name()).isEqualTo("githMind");
				assertThat(entry.url()).isEqualTo("https://githmind.example.com");
				assertThat(entry.state()).isEqualTo("unknown");
				assertThat(entry.responseTimeMs()).isNull();
				assertThat(entry.uptime30d()).isZero();
			});
			assertThat(overview.overall()).isEqualTo("unknown");
		}

		@Test
		void fallsBackToNowForCheckedAtWhenNothingHasBeenProbed() {
			stubOverview(List.of(), List.of());

			assertThat(service(List.of(GITHMIND)).getOverview().checkedAt()).isEqualTo(NOW);
		}

		@Test
		void carriesEachServicesLatestStateAndResponseTime() {
			stubOverview(
					List.of(check("githmind", NOW.minusSeconds(60), ServiceState.OPERATIONAL, 182),
							check("medi-scan", NOW.minusSeconds(60), ServiceState.DEGRADED, 2400)),
					List.of(new StatusUptimeRow("githmind", 1000, 998),
							new StatusUptimeRow("medi-scan", 1000, 996)));

			List<StatusOverviewResponse.ServiceStatus> services =
					service(List.of(GITHMIND, MEDI_SCAN)).getOverview().services();

			assertThat(services).extracting(StatusOverviewResponse.ServiceStatus::state)
					.containsExactly("operational", "degraded");
			assertThat(services).extracting(StatusOverviewResponse.ServiceStatus::responseTimeMs)
					.containsExactly(182, 2400);
			assertThat(services).extracting(StatusOverviewResponse.ServiceStatus::uptime30d)
					.containsExactly(99.8, 99.6);
		}

		@Test
		void keepsServicesInConfigurationOrderRatherThanQueryOrder() {
			// The page lists services in the order the owner wrote them; the database returns
			// them in whatever order the index walk produced.
			stubOverview(
					List.of(check("medi-scan", NOW, ServiceState.OPERATIONAL, 240),
							check("githmind", NOW, ServiceState.OPERATIONAL, 182)),
					List.of());

			assertThat(service(List.of(GITHMIND, MEDI_SCAN)).getOverview().services())
					.extracting(StatusOverviewResponse.ServiceStatus::slug)
					.containsExactly("githmind", "medi-scan");
		}

		@Test
		void overallIsTheWorstStateAmongTheServices() {
			stubOverview(
					List.of(check("githmind", NOW, ServiceState.OPERATIONAL, 182),
							check("medi-scan", NOW, ServiceState.DOWN, null)),
					List.of());

			assertThat(service(List.of(GITHMIND, MEDI_SCAN)).getOverview().overall())
					.isEqualTo("down");
		}

		@Test
		void overallIsDegradedWhenTheWorstServiceIsMerelySlow() {
			stubOverview(
					List.of(check("githmind", NOW, ServiceState.OPERATIONAL, 182),
							check("medi-scan", NOW, ServiceState.DEGRADED, 2400)),
					List.of());

			assertThat(service(List.of(GITHMIND, MEDI_SCAN)).getOverview().overall())
					.isEqualTo("degraded");
		}

		@Test
		void overallIsOperationalOnlyWhenEveryServiceIs() {
			stubOverview(
					List.of(check("githmind", NOW, ServiceState.OPERATIONAL, 182),
							check("medi-scan", NOW, ServiceState.OPERATIONAL, 240)),
					List.of());

			assertThat(service(List.of(GITHMIND, MEDI_SCAN)).getOverview().overall())
					.isEqualTo("operational");
		}

		@Test
		void anUnprobedServiceStopsTheBannerClaimingEverythingIsFine() {
			stubOverview(List.of(check("githmind", NOW, ServiceState.OPERATIONAL, 182)), List.of());

			assertThat(service(List.of(GITHMIND, MEDI_SCAN)).getOverview().overall())
					.isEqualTo("unknown");
		}

		@Test
		void aRealOutageOutranksAnUnprobedService() {
			// The reverse of the case above: with one service down and another never probed, the
			// headline worth leading with is the outage.
			stubOverview(List.of(check("medi-scan", NOW, ServiceState.DOWN, null)), List.of());

			assertThat(service(List.of(GITHMIND, MEDI_SCAN)).getOverview().overall())
					.isEqualTo("down");
		}

		@Test
		void checkedAtIsTheNewestProbeAcrossAllServices() {
			Instant newest = NOW.minusSeconds(30);
			stubOverview(
					List.of(check("githmind", NOW.minusSeconds(300), ServiceState.OPERATIONAL, 182),
							check("medi-scan", newest, ServiceState.OPERATIONAL, 240)),
					List.of());

			assertThat(service(List.of(GITHMIND, MEDI_SCAN)).getOverview().checkedAt())
					.isEqualTo(newest);
		}

		@Test
		void measuresUptimeOverTheSameThirtyDaysTheHistoryWindowCovers() {
			stubOverview(List.of(), List.of());

			service(List.of(GITHMIND)).getOverview();

			verify(repository).findUptimeSince(any(),
					eq(StatusWindow.LAST_30D.windowStart(NOW)));
		}

		@Test
		void roundsUptimeToTwoDecimalPlacesLikeTheContract() {
			stubOverview(List.of(), List.of(new StatusUptimeRow("githmind", 10000, 9982)));

			assertThat(service(List.of(GITHMIND)).getOverview().services())
					.singleElement()
					.satisfies(entry -> assertThat(entry.uptime30d()).isEqualTo(99.82));
		}

	}

	@Nested
	class History {

		@Test
		void rejectsASlugNobodyMonitors() {
			StatusService service = service(List.of(GITHMIND));

			assertThatExceptionOfType(NotFoundException.class)
					.isThrownBy(() -> service.getHistory("not-monitored", StatusWindow.LAST_24H))
					.withMessageContaining("not-monitored");
			verify(repository, never()).findBuckets(anyString(), anyString(), any());
		}

		@Test
		void answersForAConfiguredServiceThatHasNeverBeenProbed() {
			// A 404 here would be wrong: the service exists, it just has no history yet.
			stubBuckets(List.of());

			StatusHistoryResponse history =
					service(List.of(GITHMIND)).getHistory("githmind", StatusWindow.LAST_24H);

			assertThat(history.points()).hasSize(288)
					.allSatisfy(point -> {
						assertThat(point.state()).isEqualTo("unknown");
						assertThat(point.avgResponseTimeMs()).isNull();
					});
			assertThat(history.uptime()).isZero();
		}

		@Test
		void returnsTheDocumentedBucketCountForEveryWindow() {
			stubBuckets(List.of());
			StatusService service = service(List.of(GITHMIND));

			assertThat(service.getHistory("githmind", StatusWindow.LAST_24H).points()).hasSize(288);
			assertThat(service.getHistory("githmind", StatusWindow.LAST_7D).points()).hasSize(168);
			assertThat(service.getHistory("githmind", StatusWindow.LAST_30D).points()).hasSize(30);
		}

		@Test
		void echoesTheSlugAndWindowItWasAskedFor() {
			stubBuckets(List.of());

			StatusHistoryResponse history =
					service(List.of(GITHMIND)).getHistory("githmind", StatusWindow.LAST_7D);

			assertThat(history.slug()).isEqualTo("githmind");
			assertThat(history.window()).isEqualTo("7d");
		}

		@Test
		void laysStoredBucketsOverTheTimelineAndLeavesTheGapsUnknown() {
			StatusWindow window = StatusWindow.LAST_24H;
			Instant first = window.windowStart(NOW);
			Instant third = first.plus(window.stride().multipliedBy(2));
			stubBuckets(List.of(
					bucket(first, ServiceState.OPERATIONAL, 12, 12, 182.4),
					bucket(third, ServiceState.DOWN, 12, 0, null)));

			List<StatusHistoryResponse.Point> points =
					service(List.of(GITHMIND)).getHistory("githmind", window).points();

			assertThat(points.get(0).state()).isEqualTo("operational");
			assertThat(points.get(0).timestamp()).isEqualTo(first);
			assertThat(points.get(0).avgResponseTimeMs()).isEqualTo(182);
			assertThat(points.get(1).state()).isEqualTo("unknown");
			assertThat(points.get(1).avgResponseTimeMs()).isNull();
			assertThat(points.get(2).state()).isEqualTo("down");
			assertThat(points.get(2).avgResponseTimeMs()).isNull();
		}

		@Test
		void ordersPointsOldestFirstAndEndsAtTheCurrentBucket() {
			StatusWindow window = StatusWindow.LAST_24H;
			stubBuckets(List.of());

			List<StatusHistoryResponse.Point> points =
					service(List.of(GITHMIND)).getHistory("githmind", window).points();

			assertThat(points.getFirst().timestamp()).isEqualTo(window.windowStart(NOW));
			assertThat(points.getLast().timestamp()).isEqualTo(window.bucketStart(NOW));
			assertThat(points).extracting(StatusHistoryResponse.Point::timestamp).isSorted();
		}

		@Test
		void sumsUptimeAcrossTheBucketsTheChartIsDrawnFrom() {
			// 24 checks, 18 of them answering, across two buckets -> 75%.
			StatusWindow window = StatusWindow.LAST_24H;
			Instant first = window.windowStart(NOW);
			stubBuckets(List.of(
					bucket(first, ServiceState.OPERATIONAL, 12, 12, 182.0),
					bucket(first.plus(window.stride()), ServiceState.DOWN, 12, 6, 190.0)));

			assertThat(service(List.of(GITHMIND)).getHistory("githmind", window).uptime())
					.isEqualTo(75.0);
		}

		@Test
		void readsTheStrideAndWindowStartFromTheWindowItself() {
			stubBuckets(List.of());

			service(List.of(GITHMIND)).getHistory("githmind", StatusWindow.LAST_7D);

			verify(repository).findBuckets("githmind", "1 hour",
					StatusWindow.LAST_7D.windowStart(NOW));
		}

	}

	private StatusService service(List<StatusProperties.Service> services) {
		StatusProperties properties = new StatusProperties(Duration.ofMinutes(5),
				Duration.ofSeconds(10), Duration.ofSeconds(2), Duration.ofDays(35), services);
		return new StatusService(properties, repository, new StatusMapper(),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void stubOverview(List<StatusCheck> latest, List<StatusUptimeRow> uptime) {
		when(repository.findLatestPerService(any())).thenReturn(latest);
		when(repository.findUptimeSince(any(), any())).thenReturn(uptime);
	}

	private void stubBuckets(List<StatusBucketRow> buckets) {
		when(repository.findBuckets(anyString(), anyString(), any())).thenReturn(buckets);
	}

	private static StatusCheck check(String slug, Instant at, ServiceState state, Integer ms) {
		return new StatusCheck(1L, slug, at, state, ms);
	}

	private static StatusBucketRow bucket(Instant start, ServiceState state, long total, long up,
			Double avgMs) {

		return new StatusBucketRow(start, state, total, up, avgMs);
	}

}
