package dev.saq.portfolio.dto.event;

import dev.saq.portfolio.model.ModelTier;

/**
 * The payload of the SSE {@code done} event: the answer is complete, and this is who wrote it.
 *
 * <p>The last event of a successful stream. Its presence is how the client distinguishes "the
 * model finished" from "the connection dropped mid-answer", which otherwise look identical from
 * the browser.
 *
 * @param model {@code primary} or {@code fallback}, per the contract
 */
public record DoneEvent(String model) {

	/** The event for an answer written by this tier. */
	public static DoneEvent of(ModelTier tier) {
		return new DoneEvent(tier.apiValue());
	}

}
