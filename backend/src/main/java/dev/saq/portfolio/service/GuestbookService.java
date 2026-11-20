package dev.saq.portfolio.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.dto.request.CreateGuestbookEntryRequest;
import dev.saq.portfolio.dto.response.AdminGuestbookEntryResponse;
import dev.saq.portfolio.dto.response.AdminGuestbookPageResponse;
import dev.saq.portfolio.dto.response.GuestbookPageResponse;
import dev.saq.portfolio.dto.response.GuestbookSubmissionResponse;
import dev.saq.portfolio.exception.NotFoundException;
import dev.saq.portfolio.mapper.GuestbookMapper;
import dev.saq.portfolio.model.GuestbookEntry;
import dev.saq.portfolio.model.GuestbookStatus;
import dev.saq.portfolio.repository.GuestbookRepository;
import dev.saq.portfolio.util.ClientIpUtils;
import dev.saq.portfolio.util.CursorCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The guestbook: taking notes in, paging them out, and recording the owner's decisions.
 *
 * <p>Nothing a visitor posts is ever public on arrival. An entry is stored {@code PENDING} and
 * only {@link #listApproved} is reachable without the admin token, so approval is the single gate
 * between a submission and the page — not a flag a caller could ask to skip.
 *
 * <p>Submission screens in a deliberate order, mirroring {@link ContactService}: the honeypot
 * first, so an obvious bot costs one comparison and leaves no trace; then Turnstile, so a
 * rejected submission never reaches the table; and only then the insert and the notification.
 */
@Service
public class GuestbookService {

	private static final Logger log = LoggerFactory.getLogger(GuestbookService.class);

	private static final String SUBJECT_PREFIX = "[Portfolio] ";

	/** The contract's {@code limit} ceiling. A guard, not the gate — see {@link #pageSize}. */
	private static final int MAX_LIMIT = 50;

	private final GuestbookRepository guestbookRepository;

	private final TurnstileService turnstileService;

	private final EmailService emailService;

	private final GuestbookMapper guestbookMapper;

	private final ClientIpUtils clientIpUtils;

	private final CursorCodec cursorCodec;

	private final String ownerEmail;

	public GuestbookService(GuestbookRepository guestbookRepository,
			TurnstileService turnstileService, EmailService emailService,
			GuestbookMapper guestbookMapper, ClientIpUtils clientIpUtils, CursorCodec cursorCodec,
			AppProperties properties) {
		this.guestbookRepository = guestbookRepository;
		this.turnstileService = turnstileService;
		this.emailService = emailService;
		this.guestbookMapper = guestbookMapper;
		this.clientIpUtils = clientIpUtils;
		this.cursorCodec = cursorCodec;
		this.ownerEmail = properties.mail().ownerEmail();
	}

	/**
	 * Takes one note, holds it for approval, and tells the owner it is waiting.
	 *
	 * @param request  the validated submission
	 * @param clientIp the caller's address, or {@code null} if the container could not supply one.
	 *                 Used for Cloudflare's risk scoring and, hashed, as the stored fingerprint —
	 *                 the raw value is never persisted.
	 * @return the accepted response, always with status {@code pending}
	 * @throws dev.saq.portfolio.exception.TurnstileFailedException     if the token is rejected (422)
	 * @throws dev.saq.portfolio.exception.UpstreamUnavailableException if Cloudflare is unreachable (503)
	 */
	public GuestbookSubmissionResponse create(CreateGuestbookEntryRequest request, String clientIp) {
		if (request.isHoneypotTripped()) {
			// Answered exactly like a real submission, down to the shape of the id: a bot that
			// could tell the difference would simply stop filling the field in.
			log.info("Dropped a guestbook submission that filled in the honeypot.");
			return GuestbookSubmissionResponse.pending(UUID.randomUUID());
		}

		turnstileService.verify(request.turnstileToken(), clientIp);

		GuestbookEntry stored = guestbookRepository
				.save(guestbookMapper.toEntity(request, clientIpUtils.hash(clientIp)));
		notifyOwner(stored);

		return GuestbookSubmissionResponse.pending(stored.id());
	}

	/**
	 * One page of approved entries, newest first.
	 *
	 * @param cursor the cursor from a previous page, or {@code null} for the first one
	 * @param limit  how many entries the page should hold
	 * @throws dev.saq.portfolio.exception.InvalidCursorException if the cursor cannot be decoded (400)
	 */
	public GuestbookPageResponse listApproved(String cursor, int limit) {
		int size = pageSize(limit);
		List<GuestbookEntry> rows = readPage(GuestbookStatus.APPROVED, cursor, size);

		return new GuestbookPageResponse(guestbookMapper.toResponses(pageOf(rows, size)),
				nextCursor(rows, size));
	}

	/**
	 * One page of entries with the given status, newest first. Owner only.
	 *
	 * @throws dev.saq.portfolio.exception.InvalidCursorException if the cursor cannot be decoded (400)
	 */
	public AdminGuestbookPageResponse listByStatus(GuestbookStatus status, String cursor, int limit) {
		int size = pageSize(limit);
		List<GuestbookEntry> rows = readPage(status, cursor, size);

		return new AdminGuestbookPageResponse(guestbookMapper.toAdminResponses(pageOf(rows, size)),
				nextCursor(rows, size));
	}

	/**
	 * Records the owner's decision and returns the entry as it now stands.
	 *
	 * <p>Transactional because it is two statements: the targeted {@code update}, then the read
	 * that builds the response. Without one, a concurrent delete between them would surface as an
	 * empty {@code Optional} on a row that had just been updated successfully.
	 *
	 * @throws NotFoundException if there is no such entry (404)
	 */
	@Transactional
	public AdminGuestbookEntryResponse moderate(UUID id, GuestbookStatus status) {
		if (guestbookRepository.updateStatus(id, status, Instant.now()) == 0) {
			throw new NotFoundException("No such guestbook entry.");
		}

		GuestbookEntry moderated = guestbookRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("No such guestbook entry."));
		log.info("Guestbook entry {} moderated to {}", id, status);

		return guestbookMapper.toAdminResponse(moderated);
	}

	/**
	 * Deletes one entry for good. Owner only.
	 *
	 * @throws NotFoundException if there is no such entry (404)
	 */
	public void delete(UUID id) {
		if (guestbookRepository.deleteEntry(id) == 0) {
			throw new NotFoundException("No such guestbook entry.");
		}
		log.info("Guestbook entry {} deleted", id);
	}

	/**
	 * Reads one row more than the page needs, which is what tells {@link #nextCursor} whether
	 * there is another page without a second {@code count} query against the same predicate.
	 */
	private List<GuestbookEntry> readPage(GuestbookStatus status, String cursor, int size) {
		if (cursor == null || cursor.isBlank()) {
			return guestbookRepository.findFirstPage(status, size + 1);
		}

		CursorCodec.Cursor position = cursorCodec.decode(cursor);
		return guestbookRepository.findPageAfter(status, position.createdAt(), position.id(),
				size + 1);
	}

	/** The page itself: the extra row read for look-ahead is not part of it. */
	private static List<GuestbookEntry> pageOf(List<GuestbookEntry> rows, int size) {
		return (rows.size() > size) ? rows.subList(0, size) : rows;
	}

	/**
	 * The cursor for the next page, or {@code null} at the end of the list.
	 *
	 * <p>Points at the last entry actually being returned, so the next request seeks past exactly
	 * what the client already has.
	 */
	private String nextCursor(List<GuestbookEntry> rows, int size) {
		if (rows.size() <= size) {
			return null;
		}
		GuestbookEntry last = rows.get(size - 1);
		return cursorCodec.encode(last.createdAt(), last.id());
	}

	/**
	 * Keeps a page within the contract's bounds.
	 *
	 * <p>The controller already rejects anything outside 1–50 with a 400, so this never fires over
	 * HTTP. It is here because an unbounded {@code limit} is the difference between a page query
	 * and a full table scan, and that should not depend on one annotation elsewhere.
	 */
	private static int pageSize(int limit) {
		return Math.clamp(limit, 1, MAX_LIMIT);
	}

	/**
	 * Tells the owner a note is waiting.
	 *
	 * <p>Fire-and-forget: {@link EmailService} is asynchronous, so the visitor is answered without
	 * waiting for the provider. There is no delivery-status column to correct here as there is for
	 * contact messages — the entry is safe in the table either way, and the owner finds it on
	 * {@code /admin} even if the notification never arrives. A failure is logged rather than
	 * swallowed, because a silently broken notification would look exactly like an empty inbox.
	 */
	private void notifyOwner(GuestbookEntry entry) {
		emailService.send(ownerEmail, subjectFor(entry), bodyFor(entry), null)
				.whenComplete((ignored, failure) -> {
					if (failure != null) {
						log.error("Could not notify the owner about guestbook entry {}", entry.id(),
								failure);
					}
				});
	}

	private static String subjectFor(GuestbookEntry entry) {
		return SUBJECT_PREFIX + "New guestbook entry from " + singleLine(entry.name());
	}

	private static String bodyFor(GuestbookEntry entry) {
		return new StringBuilder("A new guestbook entry is waiting for approval.\n\n")
				.append("From:  ").append(entry.name()).append("\n")
				.append("Entry: ").append(entry.id()).append("\n\n")
				.append(entry.message()).append("\n\n")
				.append("Approve or reject it on the /admin page.\n")
				.toString();
	}

	/**
	 * Flattens anything that could break a header out of a subject line.
	 *
	 * <p>The provider is given JSON rather than a raw MIME message, so a newline here cannot
	 * inject a header today — but a subject is header data, and keeping it on one line means that
	 * stays true if the transport ever changes.
	 */
	private static String singleLine(String value) {
		return value.replaceAll("[\\r\\n]+", " ").strip();
	}

}
