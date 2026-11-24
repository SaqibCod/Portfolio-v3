package dev.saq.portfolio.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.saq.portfolio.config.ChatProperties;
import dev.saq.portfolio.dto.internal.RetrievedContext;
import dev.saq.portfolio.dto.request.ChatMessageRequest;
import dev.saq.portfolio.dto.request.ChatRequest;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Assembles the prompt: the rules, the retrieved context, and the conversation so far.
 *
 * <p>The rules live in {@code resources/prompts/system.st} rather than in this file, because they
 * are the product and should be reviewable as prose by whoever owns the site. They are rendered
 * once per request with the retrieved chunks inlined, which is the only way the "answer only from
 * the context" instruction and the context itself can be guaranteed to agree.
 *
 * <p>Two ordering decisions are load-bearing:
 * <ul>
 * <li><b>The context is in the system message, not the user message.</b> A visitor's text is
 * untrusted and is the thing most likely to contain "ignore your instructions"; keeping the
 * retrieved content out of it means an injection attempt cannot pose as site content.</li>
 * <li><b>The conversation is replayed as real user and assistant messages</b> rather than
 * flattened into one block. A model given a transcript as prose will sometimes continue the
 * transcript — writing the visitor's next question — instead of answering.</li>
 * </ul>
 */
@Service
public class PromptService {

	/** Template variable: the site owner's name. */
	private static final String NAME_VARIABLE = "name";

	/** Template variable: where to send a visitor whose question is not covered. */
	private static final String CONTACT_URL_VARIABLE = "contactUrl";

	/** Template variable: the retrieved chunks. */
	private static final String CONTEXT_VARIABLE = "context";

	/**
	 * What the model is told when retrieval found nothing.
	 *
	 * <p>An explicit sentence rather than an empty string. An empty {@code CONTEXT:} heading
	 * reads, to a model, like a formatting accident worth working around; being told plainly that
	 * there is nothing is what reliably produces "I don't have that on the site" instead of a
	 * plausible invention.
	 */
	private static final String NO_CONTEXT = "(No content on this site matched the question.)";

	private final PromptTemplate systemTemplate;

	private final ChatProperties properties;

	public PromptService(ChatProperties properties, ResourceLoader resourceLoader) {
		this.properties = properties;

		Resource prompt = resourceLoader.getResource(properties.systemPromptResource());
		if (!prompt.exists()) {
			// Fail at startup, not at the first question. A chatbot running without its rules
			// would answer anything about anyone, which is the one failure mode worth refusing
			// to boot over.
			throw new IllegalStateException(
					"System prompt not found at " + properties.systemPromptResource());
		}
		this.systemTemplate = PromptTemplate.builder().resource(prompt).build();
	}

	/**
	 * The prompt for one request.
	 *
	 * @param request   the validated conversation; its last message is the question
	 * @param retrieved what retrieval found, possibly nothing
	 */
	public Prompt build(ChatRequest request, RetrievedContext retrieved) {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage(renderSystemPrompt(retrieved)));

		for (ChatMessageRequest message : request.recentMessages(properties.maxMessages())) {
			messages.add(switch (message.role()) {
				case USER -> new UserMessage(message.content());
				case ASSISTANT -> new AssistantMessage(message.content());
			});
		}

		return new Prompt(messages);
	}

	/** The rules with the retrieved chunks inlined. */
	private String renderSystemPrompt(RetrievedContext retrieved) {
		return systemTemplate.render(Map.of(
				NAME_VARIABLE, properties.ownerName(),
				CONTACT_URL_VARIABLE, properties.contactUrl(),
				CONTEXT_VARIABLE, contextBlock(retrieved)));
	}

	/**
	 * The chunks as a numbered block.
	 *
	 * <p>Numbered and separated so the boundary between two unrelated excerpts is unmistakable.
	 * Run together, a paragraph about one project and a paragraph about another read as one
	 * passage, and the model will happily attribute a detail from the second to the first.
	 */
	private static String contextBlock(RetrievedContext retrieved) {
		if (retrieved.isEmpty()) {
			return NO_CONTEXT;
		}

		StringBuilder block = new StringBuilder();
		List<String> chunks = retrieved.chunks();
		for (int i = 0; i < chunks.size(); i++) {
			if (i > 0) {
				block.append("\n\n");
			}
			block.append("--- excerpt ").append(i + 1).append(" ---\n").append(chunks.get(i));
		}
		return block.toString();
	}

}
