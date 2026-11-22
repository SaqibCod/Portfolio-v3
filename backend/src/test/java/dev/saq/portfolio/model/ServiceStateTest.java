package dev.saq.portfolio.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The severity order is a product decision rather than an implementation detail -- it decides
 * which headline the status page leads with -- so it is pinned here rather than left to the
 * declaration order it happens to match.
 */
class ServiceStateTest {

	@Test
	void spellsEveryStateTheWayTheContractDoes() {
		assertThat(ServiceState.OPERATIONAL.toApi()).isEqualTo("operational");
		assertThat(ServiceState.DEGRADED.toApi()).isEqualTo("degraded");
		assertThat(ServiceState.DOWN.toApi()).isEqualTo("down");
		assertThat(ServiceState.UNKNOWN.toApi()).isEqualTo("unknown");
	}

	@Test
	void roundTripsEveryApiValue() {
		for (ServiceState state : ServiceState.values()) {
			assertThat(ServiceState.fromApi(state.toApi())).isEqualTo(state);
		}
	}

	@Test
	void toleratesSurroundingSpaceAndCase() {
		assertThat(ServiceState.fromApi("  Operational ")).isEqualTo(ServiceState.OPERATIONAL);
	}

	@Test
	void rejectsAnythingElse() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> ServiceState.fromApi("broken"));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> ServiceState.fromApi(null));
	}

	@Test
	void treatsOnlyUnknownAsUnstorable() {
		assertThat(ServiceState.OPERATIONAL.isStored()).isTrue();
		assertThat(ServiceState.DEGRADED.isStored()).isTrue();
		assertThat(ServiceState.DOWN.isStored()).isTrue();
		assertThat(ServiceState.UNKNOWN.isStored()).isFalse();
	}

	@Test
	void ranksAnUnprobedServiceWorseThanAHealthyOneButBetterThanAMeasuredProblem() {
		assertThat(ServiceState.OPERATIONAL.severity())
				.isLessThan(ServiceState.UNKNOWN.severity());
		assertThat(ServiceState.UNKNOWN.severity())
				.isLessThan(ServiceState.DEGRADED.severity());
		assertThat(ServiceState.DEGRADED.severity())
				.isLessThan(ServiceState.DOWN.severity());
	}

	@Test
	void pickingTheWorstOfSeveralStates() {
		assertThat(ServiceState.worst(List.of(ServiceState.OPERATIONAL, ServiceState.OPERATIONAL)))
				.isEqualTo(ServiceState.OPERATIONAL);
		assertThat(ServiceState.worst(List.of(ServiceState.OPERATIONAL, ServiceState.UNKNOWN)))
				.isEqualTo(ServiceState.UNKNOWN);
		assertThat(ServiceState.worst(List.of(ServiceState.UNKNOWN, ServiceState.DEGRADED)))
				.isEqualTo(ServiceState.DEGRADED);
		assertThat(ServiceState.worst(List.of(ServiceState.DOWN, ServiceState.UNKNOWN)))
				.isEqualTo(ServiceState.DOWN);
	}

	@Test
	void theWorstOfNothingIsUnknown() {
		// What an overview with no services configured reports: nothing is wrong, but nothing is
		// known either, and claiming "all systems operational" over an empty list would be a lie.
		assertThat(ServiceState.worst(List.of())).isEqualTo(ServiceState.UNKNOWN);
	}

}
