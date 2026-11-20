package dev.saq.portfolio.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.saq.portfolio.model.GuestbookEntry;
import dev.saq.portfolio.model.GuestbookStatus;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link GuestbookEntry}.
 *
 * <p>Paging is keyset ("seek"), not offset: a page asks for the rows ordered after the last one
 * the caller already has. {@code offset} would make page <i>n</i> cost <i>n</i> pages of work and
 * would silently drop or repeat an entry whenever one was approved between two requests, which on
 * a list ordered by approval is the normal case rather than the exceptional one.
 *
 * <p>The ordering is {@code (created_at desc, id desc)}, and the cursor comparison uses the same
 * pair as a row value. The id is in there only as a tie-breaker: {@code created_at} is not unique,
 * and without it two entries sharing a timestamp could straddle a page boundary and be served
 * twice or not at all. {@code guestbook_status_created_idx} matches this shape exactly.
 *
 * <p>Two methods rather than one with a nullable cursor: a string-based {@code @Query} in Spring
 * Data JDBC cannot take {@code Pageable} or {@code Limit}, so the limit is bound as a plain
 * parameter, and folding "no cursor" into the same SQL would mean comparing against typed nulls
 * for no gain. Both ask for one row more than the caller wanted — see
 * {@link dev.saq.portfolio.service.GuestbookService}, which uses the extra row to decide whether
 * there is a next page without running a second count query.
 */
public interface GuestbookRepository extends ListCrudRepository<GuestbookEntry, UUID> {

	/**
	 * The newest entries with the given status.
	 *
	 * @param limit how many rows to read; pass one more than the page size
	 */
	@Query("""
			select id, name, message, status, ip_hash, created_at, moderated_at
			from guestbook_entry
			where status = :status
			order by created_at desc, id desc
			limit :limit""")
	List<GuestbookEntry> findFirstPage(@Param("status") GuestbookStatus status,
			@Param("limit") int limit);

	/**
	 * The next entries with the given status, continuing after one the caller already has.
	 *
	 * @param createdAt the cursor entry's creation time
	 * @param id        the cursor entry's id, which breaks ties on {@code createdAt}
	 * @param limit     how many rows to read; pass one more than the page size
	 */
	@Query("""
			select id, name, message, status, ip_hash, created_at, moderated_at
			from guestbook_entry
			where status = :status
			  and (created_at, id) < (:createdAt, :id)
			order by created_at desc, id desc
			limit :limit""")
	List<GuestbookEntry> findPageAfter(@Param("status") GuestbookStatus status,
			@Param("createdAt") Instant createdAt, @Param("id") UUID id, @Param("limit") int limit);

	/**
	 * Records the owner's decision on one entry.
	 *
	 * <p>A targeted {@code update} rather than {@code save(entity)}: only these two columns may
	 * change, and nothing moderation does should be able to rewrite the note a visitor left.
	 *
	 * @return the number of rows changed — {@code 0} if the entry no longer exists
	 */
	@Modifying
	@Query("update guestbook_entry set status = :status, moderated_at = :moderatedAt where id = :id")
	int updateStatus(@Param("id") UUID id, @Param("status") GuestbookStatus status,
			@Param("moderatedAt") Instant moderatedAt);

	/**
	 * Deletes one entry.
	 *
	 * <p>{@code deleteById} is silent about whether anything was there, and the contract
	 * distinguishes a 204 from a 404, so the row count is what the service needs.
	 *
	 * @return the number of rows deleted — {@code 0} if the entry was already gone
	 */
	@Modifying
	@Query("delete from guestbook_entry where id = :id")
	int deleteEntry(@Param("id") UUID id);

}
