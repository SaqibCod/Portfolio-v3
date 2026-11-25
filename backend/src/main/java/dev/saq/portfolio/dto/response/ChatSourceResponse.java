package dev.saq.portfolio.dto.response;

/**
 * One page the assistant's answer came from, as the chat widget renders it.
 *
 * <p>Sent once per answer in the SSE {@code sources} event, and only ever built from chunks that
 * were actually in the prompt — a citation here is a claim that the answer is grounded in that
 * page, so inventing one would be worse than offering none.
 *
 * <p>Matches {@code ChatSource} in {@code docs/api/portfolio-api.yaml}.
 *
 * @param title   the page's name, from the knowledge file's frontmatter
 * @param url     site-relative path, e.g. {@code /projects/githmind}
 * @param snippet the opening of the retrieved chunk, capped at the contract's 300 characters.
 *                Context for the link rather than a summary, so the visitor can tell which part
 *                of a long page the answer leaned on
 */
public record ChatSourceResponse(
		String title,
		String url,
		String snippet) {

}
