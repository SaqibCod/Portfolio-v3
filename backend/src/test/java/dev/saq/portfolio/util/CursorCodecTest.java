package dev.saq.portfolio.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import dev.saq.portfolio.exception.InvalidCursorException;
import dev.saq.portfolio.util.CursorCodec.Cursor;

import org.junit.jupiter.api.Test;

/** Round-trips, and every way a cursor can be wrong. */
class CursorCodecTest {

	private static final Instant CREATED_AT = Instant.parse("2026-11-20T10:15:00Z");

	private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	private final CursorCodec codec = new CursorCodec();

	@Test
	void roundTripsATimestampAndId() {
		Cursor decoded = codec.decode(codec.encode(CREATED_AT, ID));

		assertThat(decoded.createdAt()).isEqualTo(CREATED_AT);
		assertThat(decoded.id()).isEqualTo(ID);
	}

	@Test
	void keepsSubSecondPrecision() {
		Instant precise = Instant.parse("2026-11-20T10:15:00.123456Z");

		assertThat(codec.decode(codec.encode(precise, ID)).createdAt()).isEqualTo(precise);
	}

	@Test
	void producesTheSameCursorForTheSamePosition() {
		// Stable output matters: an unstable cursor would make a page boundary move between two
		// identical requests.
		assertThat(codec.encode(CREATED_AT, ID)).isEqualTo(codec.encode(CREATED_AT, ID));
	}

	@Test
	void producesUrlSafeOutputThatNeedsNoEscaping() {
		String cursor = codec.encode(CREATED_AT, ID);

		// The value travels in a query string, where + is a space and / ends a path segment.
		assertThat(cursor).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void doesNotShowTheTimestampOrIdInTheClear() {
		// Not a security property — only a reminder that the contract calls this value opaque, so
		// nothing should look parseable enough to invite a client to try.
		String cursor = codec.encode(CREATED_AT, ID);

		assertThat(cursor).doesNotContain("2026").doesNotContain(ID.toString());
	}

	@Test
	void rejectsNull() {
		assertThatExceptionOfType(InvalidCursorException.class).isThrownBy(() -> codec.decode(null));
	}

	@Test
	void rejectsBlank() {
		assertThatExceptionOfType(InvalidCursorException.class).isThrownBy(() -> codec.decode("   "));
	}

	@Test
	void rejectsSomethingThatIsNotBase64() {
		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> codec.decode("not base64!!"));
	}

	@Test
	void rejectsBase64WithNoSeparator() {
		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> codec.decode(base64Of("2026-11-20T10:15:00Z")));
	}

	@Test
	void rejectsBase64WithASecondSeparator() {
		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> codec.decode(base64Of("2026-11-20T10:15:00Z|" + ID + "|extra")));
	}

	@Test
	void rejectsAnUnparseableTimestamp() {
		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> codec.decode(base64Of("yesterday|" + ID)));
	}

	@Test
	void rejectsAnUnparseableId() {
		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> codec.decode(base64Of("2026-11-20T10:15:00Z|not-a-uuid")));
	}

	@Test
	void rejectsAnEmptyPayload() {
		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> codec.decode(base64Of("|")));
	}

	@Test
	void neverEchoesTheRejectedValueBack() {
		String hostile = base64Of("<script>alert(1)</script>|" + ID);

		assertThatExceptionOfType(InvalidCursorException.class)
				.isThrownBy(() -> codec.decode(hostile))
				.withMessageNotContaining("script");
	}

	private static String base64Of(String payload) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

}
