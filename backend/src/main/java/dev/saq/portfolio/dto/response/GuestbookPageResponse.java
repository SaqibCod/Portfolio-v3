package dev.saq.portfolio.dto.response;

import java.util.List;

/**
 * One page of approved entries, matching the contract's {@code GuestbookPage}.
 *
 * @param items      the page, newest first
 * @param nextCursor the cursor for the following page, or {@code null} at the end of the list.
 *                   Required by the contract and therefore always serialised, null included: a
 *                   client checks this field to decide whether to offer "load more", and an
 *                   absent key and an explicit null would have to be treated the same anyway.
 */
public record GuestbookPageResponse(List<GuestbookEntryResponse> items, String nextCursor) {
}
