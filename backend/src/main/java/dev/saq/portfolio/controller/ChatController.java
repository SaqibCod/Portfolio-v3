package dev.saq.portfolio.controller;

import dev.saq.portfolio.annotation.RateLimited;
import dev.saq.portfolio.dto.request.ChatRequest;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.ChatService;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import reactor.core.publisher.Flux;

/**
 * The "Ask about me" endpoint.
 *
 * <p>A {@code POST} rather than a {@code GET} with {@code EventSource}, because the conversation
 * is a body: the endpoint is stateless and the client resends up to ten messages, which is more
 * than a query string should carry and not something to cache. The browser side is therefore
 * {@code fetch} plus a stream reader rather than the {@code EventSource} API.
 *
 * <p>Returning a {@code Flux} of {@code ServerSentEvent} from a Spring MVC controller is
 * supported: {@code ReactiveTypeHandler} recognises the type, opens an {@code SseEmitter} and
 * adapts each element into a named event. No WebFlux is involved.
 *
 * <p>{@link RateLimited} with {@link RateLimitPolicy#CHAT} is the only paid endpoint on this API,
 * and its budget is the lowest of all of them for that reason.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	/**
	 * Streams an answer to the last message in the conversation.
	 *
	 * <p>The 503 case is reached before this method returns anything: {@link ChatService#stream}
	 * does not hand back a stream until a model has produced a first token, so "neither model
	 * answered" is still an ordinary exception travelling to the handler, and the client gets a
	 * real status code with a Problem body instead of an empty 200.
	 *
	 * @param request oldest message first, at most ten, the last from the visitor
	 * @return {@code token}* → {@code sources} → {@code done}, or a single {@code error} event if
	 *         the model fails once the answer is already flowing
	 */
	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@RateLimited(RateLimitPolicy.CHAT)
	public Flux<ServerSentEvent<Object>> chat(@Valid @RequestBody ChatRequest request) {
		return chatService.stream(request);
	}

}
