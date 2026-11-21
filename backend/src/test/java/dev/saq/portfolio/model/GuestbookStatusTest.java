package dev.saq.portfolio.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one place the API's lowercase statuses meet the uppercase column values, so the mapping is
 * pinned in both directions here and nowhere else.
 */
class GuestbookStatusTest {

	@Test
	void spellsEachStatusTheWayTheContractDoes() {
		assertThat(GuestbookStatus.PENDING.toApi()).isEqualTo("pending");
		assertThat(GuestbookStatus.APPROVED.toApi()).isEqualTo("approved");
		assertThat(GuestbookStatus.REJECTED.toApi()).isEqualTo("rejected");
	}

	@Test
	void readsEachStatusBackFromTheContractSpelling() {
		assertThat(GuestbookStatus.fromApi("pending")).isEqualTo(GuestbookStatus.PENDING);
		assertThat(GuestbookStatus.fromApi("approved")).isEqualTo(GuestbookStatus.APPROVED);
		assertThat(GuestbookStatus.fromApi("rejected")).isEqualTo(GuestbookStatus.REJECTED);
	}

	@ParameterizedTest
	@EnumSource(GuestbookStatus.class)
	void roundTripsEveryStatus(GuestbookStatus status) {
		assertThat(GuestbookStatus.fromApi(status.toApi())).isEqualTo(status);
	}

	@ParameterizedTest
	@ValueSource(strings = { " pending", "pending ", "PENDING", "Approved" })
	void toleratesSurroundingSpaceAndCase(String value) {
		// A query parameter is hand-editable, and none of these is ambiguous about what it means.
		assertThat(GuestbookStatus.fromApi(value)).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "queued", "deleted", "pending,approved" })
	void refusesAnythingThatIsNotOneOfTheThree(String value) {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> GuestbookStatus.fromApi(value));
	}

	@Test
	void refusesNull() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> GuestbookStatus.fromApi(null));
	}

	@Test
	void keepsTheConstantNamesTheMigrationConstrains() {
		// V3__guestbook.sql pins these three names in a check constraint, and the column stores
		// the constant name, so a rename here is a migration.
		assertThat(GuestbookStatus.values()).extracting(Enum::name)
				.containsExactly("PENDING", "APPROVED", "REJECTED");
	}

}
