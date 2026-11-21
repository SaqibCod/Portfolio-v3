package dev.saq.portfolio.dto.request;

import dev.saq.portfolio.model.GuestbookStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The owner's decision on one entry, matching the contract's {@code ModerateEntry}.
 *
 * <p>A validated {@code String} rather than a {@link GuestbookStatus} field: binding the enum
 * directly would make an unknown value a Jackson parse failure, which is a 400 with nothing in it
 * about which field was wrong. This way a bad value is an ordinary field error, and the two values
 * the contract allows here are enforced in one place.
 *
 * <p>{@code pending} is deliberately not accepted. The contract's enum for this endpoint is
 * {@code [approved, rejected]} — moderation is a decision, and "un-decide this" is not one. The
 * wider three-value enum belongs to the admin list's {@code status} filter instead.
 *
 * @param status {@code approved} or {@code rejected}
 */
public record ModerateEntryRequest(
		@NotBlank(message = "is required")
		@Pattern(regexp = "approved|rejected", message = "must be approved or rejected")
		String status) {

	/**
	 * The decision as the domain spells it.
	 *
	 * <p>Safe to call only on a validated instance: {@link GuestbookStatus#fromApi} throws on
	 * anything the {@code @Pattern} above would already have rejected.
	 */
	public GuestbookStatus toStatus() {
		return GuestbookStatus.fromApi(status);
	}

}
