package dev.saq.portfolio.repository;

import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.model.KnowledgeSource;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link KnowledgeSource}: read what was ingested, record what just was, forget
 * a file that is gone.
 *
 * <p>Deliberately not a {@code CrudRepository}. Spring Data JDBC decides insert-versus-update
 * from whether the id is null, and this entity's id is a path that is always set — so
 * {@code save()} on a file being ingested for the first time would issue an {@code UPDATE} that
 * silently matches no rows. {@link #upsert} states the intent in SQL instead, which is also the
 * only statement shape that is correct when two instances start at once.
 */
public interface KnowledgeSourceRepository extends Repository<KnowledgeSource, String> {

	/**
	 * Every file ingested so far. Small by construction — one row per markdown file — so the
	 * whole table is read at once and compared in memory rather than queried per file.
	 */
	@Query("select path, content_hash, ingested_at from knowledge_source")
	List<KnowledgeSource> findAll();

	/** Records a file as ingested, whether or not it had been seen before. */
	@Modifying
	@Query("""
			insert into knowledge_source (path, content_hash, ingested_at)
			values (:path, :contentHash, :ingestedAt)
			on conflict (path) do update
			  set content_hash = excluded.content_hash,
			      ingested_at = excluded.ingested_at
			""")
	void upsert(@Param("path") String path, @Param("contentHash") String contentHash,
			@Param("ingestedAt") Instant ingestedAt);

	/** Forgets a file, after its chunks have been deleted. */
	@Modifying
	@Query("delete from knowledge_source where path = :path")
	void deleteByPath(@Param("path") String path);

}
