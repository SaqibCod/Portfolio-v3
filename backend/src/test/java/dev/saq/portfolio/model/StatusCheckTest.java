package dev.saq.portfolio.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class StatusCheckTest {

	@Test
	void buildsAnUnsavedCheck() {
		Instant at = Instant.parse("2026-11-22T10:35:00Z");

		StatusCheck check = StatusCheck.record("githmind", at, ServiceState.DEGRADED, 2400);

		assertThat(check.id()).isNull();
		assertThat(check.serviceSlug()).isEqualTo("githmind");
		assertThat(check.checkedAt()).isEqualTo(at);
		assertThat(check.state()).isEqualTo(ServiceState.DEGRADED);
		assertThat(check.responseTimeMs()).isEqualTo(2400);
	}

	@Test
	void leavesTheResponseTimeOutWhenNothingWasMeasured() {
		StatusCheck check = StatusCheck.record("githmind", Instant.now(), ServiceState.DOWN, null);

		assertThat(check.responseTimeMs()).isNull();
	}

	@Test
	void refusesToBuildACheckTheColumnWouldReject() {
		// The check constraint in V4__status.sql would catch this too, but as a constraint
		// violation on insert rather than as the programming error it is.
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
				() -> StatusCheck.record("githmind", Instant.now(), ServiceState.UNKNOWN, null));
	}

}
