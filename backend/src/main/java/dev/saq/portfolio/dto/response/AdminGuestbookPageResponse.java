package dev.saq.portfolio.dto.response;

import java.util.List;

/**
 * One page of entries for moderation, matching the contract's {@code AdminGuestbookPage}.
 *
 * @param items      the page, newest first
 * @param nextCursor the cursor for the following page, or {@code null} at the end of the list
 */
public record AdminGuestbookPageResponse(List<AdminGuestbookEntryResponse> items, String nextCursor) {
}
