package dev.saq.portfolio.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The two chat clients the assistant answers with: Gemini, and whatever
 * {@code FALLBACK_LLM_BASE_URL} points at.
 *
 * <p>Getting two providers into one application is a matter of what is <em>not</em> configured.
 * {@code spring.ai.model.chat} selects a single provider, but both the google-genai and the
 * openai chat auto-configurations declare {@code matchIfMissing = true} — so leaving that
 * property unset is what activates both, and naming either one would silently remove the
 * fallback. {@code application.yml} says so where someone is likely to "tidy it up".
 *
 * <p>Two {@code ChatModel} beans make Spring AI's auto-configured {@code ChatClient.Builder}
 * ambiguous, so {@code spring.ai.chat.client.enabled=false} turns it off and the clients are
 * built here instead. Nothing is lost by that: a single default client is exactly the thing this
 * feature cannot use, because the whole point is choosing between two at request time.
 *
 * <p>Both clients are deliberately bare — no default system prompt, no advisors. The system
 * prompt is assembled per request by {@link dev.saq.portfolio.service.PromptService} around the
 * chunks that were retrieved for that question, and a default baked in here would be invisible at
 * the call site and impossible to keep in step with the context.
 */
@Configuration(proxyBeanMethods = false)
public class AiModelConfig {

	/** Bean name of the primary client, for {@code @Qualifier} at the injection point. */
	public static final String PRIMARY_CHAT_CLIENT = "primaryChatClient";

	/** Bean name of the fallback client. */
	public static final String FALLBACK_CHAT_CLIENT = "fallbackChatClient";

	/**
	 * Gemini.
	 *
	 * @param chatModel injected by the bean name {@code GoogleGenAiChatAutoConfiguration} gives
	 *                  it, not by type — there are two {@code ChatModel} beans, and which one is
	 *                  primary is a decision this class exists to make rather than something to
	 *                  leave to bean ordering
	 */
	@Bean(PRIMARY_CHAT_CLIENT)
	ChatClient primaryChatClient(@Qualifier("googleGenAiChatModel") ChatModel chatModel) {
		return ChatClient.builder(chatModel).build();
	}

	/** The OpenAI-compatible endpoint, used only when the primary produces no first token. */
	@Bean(FALLBACK_CHAT_CLIENT)
	ChatClient fallbackChatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
		return ChatClient.builder(chatModel).build();
	}

}
