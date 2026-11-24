package dev.saq.portfolio.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * How the "Ask about me" chatbot retrieves, prompts and gives up.
 *
 * <p>Every value here is a knob on the same trade: how much of the site to put in front of the
 * model, and how long to wait for it. Defaults are tuned for a free-tier Gemini key answering
 * questions about a handful of markdown files, not for a large corpus.
 *
 * @param topK                 how many chunks retrieval may return. Small on purpose: an answer
 *                             capped at ~150 words cannot use more, and every extra chunk is
 *                             budget spent on the question rather than the answer
 * @param similarityThreshold  cosine similarity floor, 0..1. The honesty setting: a question the
 *                             site does not cover has to retrieve *nothing* so the model has
 *                             nothing to work from and says so. Too low and a near-miss chunk
 *                             becomes a confident wrong answer
 * @param maxContextChars      ceiling on the assembled {@code <context>} block, applied after
 *                             {@code topK}. Guards the token budget when the splitter produces
 *                             unusually long chunks
 * @param firstTokenTimeout    how long the primary model has to produce its first token before
 *                             the request is handed to the fallback. Only the first token is
 *                             timed; once the answer is flowing it is never interrupted
 * @param systemPromptResource classpath location of the prompt template. A resource rather than
 *                             a string so the rules can be reviewed like any other content
 * @param knowledgeLocation    Ant-style pattern matching the markdown knowledge files
 * @param maxMessages          how many trailing conversation messages reach the prompt. Matches
 *                             {@code ChatRequest.messages} maxItems in the contract
 * @param ingestOnStartup      whether {@code ApplicationReadyEvent} triggers ingestion. False in
 *                             tests, which drive ingestion by hand
 */
@Validated
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
		@Min(1) @Max(20) @DefaultValue("5") int topK,
		@DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.6") double similarityThreshold,
		@Min(500) @DefaultValue("8000") int maxContextChars,
		@NotNull @DefaultValue("15s") Duration firstTokenTimeout,
		@NotBlank @DefaultValue("classpath:prompts/system.st") String systemPromptResource,
		@NotBlank @DefaultValue("classpath:knowledge/*.md") String knowledgeLocation,
		@Min(1) @Max(10) @DefaultValue("10") int maxMessages,
		@DefaultValue("true") boolean ingestOnStartup) {

}
