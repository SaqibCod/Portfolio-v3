package dev.saq.portfolio.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.dto.response.StatusHistoryResponse;
import dev.saq.portfolio.dto.response.StatusOverviewResponse;
import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusWindow;

import org.junit.jupiter.api.Test;

class StatusMapperTest {

	private static final Instant AT = Instant.parse("2026-11-22T10:35:00Z");

	private static final StatusProperties.Service GITHMIND = new StatusProperties.Service(
			"githmind", "githMind", "https://githmind.example.com", "/actuator/health");

	private final StatusMapper mapper = new StatusMapper();

	@Test
	void takesTheNameAndUrlFromConfigurationAndTheStateFromTheProbe() {
		StatusOverviewResponse.ServiceStatus status =
				mapper.toServiceStatus(GITHMIND, ServiceState.DEGRADED, 2400, 99.8231);

		assertThat(status.slug()).isEqualTo("githmind");
		assertThat(status.name()).isEqualTo("githMind");
		assertThat(status.url()).isEqualTo("https://githmind.example.com");
		assertThat(status.state()).isEqualTo("degraded");
		assertThat(status.responseTimeMs()).isEqualTo(2400);
	}

	@Test
	void roundsUptimeToTheTwoDecimalPlacesTheContractShows() {
		assertThat(mapper.toServiceStatus(GITHMIND, ServiceState.OPERATIONAL, 182, 99.8231)
				.uptime30d()).isEqualTo(99.82);
		assertThat(mapper.toServiceStatus(GITHMIND, ServiceState.OPERATIONAL, 182, 99.8250)
				.uptime30d()).isEqualTo(99.83);
		assertThat(mapper.toServiceStatus(GITHMIND, ServiceState.OPERATIONAL, 182, 100.0)
				.uptime30d()).isEqualTo(100.0);
	}

	@Test
	void roundsHalfUpRatherThanToTheNearestBinaryDouble() {
		// 1/3 of 100 is the classic case where the x * 100 / 100.0 shorthand serialises as
		// 33.330000000000005.
		assertThat(mapper.toHistory("githmind", StatusWindow.LAST_24H, 100.0 / 3, List.of())
				.uptime()).isEqualTo(33.33);
	}

	@Test
	void spellsTheOverallStateAndKeepsTheServicesAndTimestampAsGiven() {
		StatusOverviewResponse.ServiceStatus entry =
				mapper.toServiceStatus(GITHMIND, ServiceState.OPERATIONAL, 182, 100);

		StatusOverviewResponse overview =
				mapper.toOverview(ServiceState.UNKNOWN, List.of(entry), AT);

		assertThat(overview.overall()).isEqualTo("unknown");
		assertThat(overview.services()).containsExactly(entry);
		assertThat(overview.checkedAt()).isEqualTo(AT);
	}

	@Test
	void spellsAPointsStateAndPassesItsAverageThrough() {
		StatusHistoryResponse.Point point = mapper.toPoint(AT, ServiceState.DOWN, null);

		assertThat(point.timestamp()).isEqualTo(AT);
		assertThat(point.state()).isEqualTo("down");
		assertThat(point.avgResponseTimeMs()).isNull();
	}

	@Test
	void spellsTheWindowTheWayTheContractDoes() {
		assertThat(mapper.toHistory("githmind", StatusWindow.LAST_7D, 100, List.of()).window())
				.isEqualTo("7d");
		assertThat(mapper.toHistory("githmind", StatusWindow.LAST_30D, 100, List.of()).window())
				.isEqualTo("30d");
	}

}
