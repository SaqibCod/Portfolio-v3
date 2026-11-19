package dev.saq.portfolio.repository;

import java.util.UUID;

import dev.saq.portfolio.model.ContactMessage;
import dev.saq.portfolio.model.DeliveryStatus;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Data access for {@link ContactMessage}. */
public interface ContactMessageRepository extends ListCrudRepository<ContactMessage, UUID> {

	/**
	 * Advances only the delivery status of one message.
	 *
	 * <p>A targeted {@code update} rather than {@code save(entity)}: this runs from the mail
	 * provider's completion callback, on another thread and long after the row was written, so it
	 * must not carry a stale copy of every other column back to the database.
	 *
	 * @return the number of rows changed — {@code 0} if the message is already gone
	 */
	@Modifying
	@Query("update contact_message set status = :status where id = :id")
	int updateStatus(@Param("id") UUID id, @Param("status") DeliveryStatus status);

}
