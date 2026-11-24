package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.saq.portfolio.service.KnowledgeIngestionService;
import dev.saq.testsupport.StubChatModel;
import dev.saq.testsupport.SwitchableChatModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * The endpoint over real HTTP, with real retrieval out of pgvector and the real SSE wire format.
 *
 * <p>This is the only test that reads the bytes a browser would read. Everything between the
 * controller and the socket is assumed by the other tests and proved here: that Spring MVC turns
 * a {@code Flux<ServerSentEvent>} into {@code event:} / {@code data:} lines at all, that the
 * event names match the contract's description, and that a 503 arrives as a status code with a
 * Problem body rather than as a 200 carrying bad news.
 *
 * <p>The knowledge base is the {@code knowledge-test} fixtures, ingested once per test, and both
 * models are {@link SwitchableChatModel}s scripted per case. Nothing reaches a provider: under
 * the test profile there is no provider auto-configuration in the context at all.
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatIntegrationTests {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String CHAT_PATH = "/api/v1/chat";

	/** A question whose words appear only in the alpha fixture, so retrieval has a clear winner. */
	private static final String ARTICHOKE_QUESTION =
			"{\"messages\":[{\"role\":\"user\",\"content\":\"Tell me about artichoke recipes\"}]}";

	@LocalServerPort
	private int port;

	@Autowired
	@Qualifier("googleGenAiChatModel")
	private SwitchableChatModel primaryModel;

	@Autowired
	@Qualifier("openAiChatModel")
	private SwitchableChatModel fallbackModel;

	@Autowired
	private KnowledgeIngestionService ingestionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient client;

	@BeforeEach
	void setUp() {
		client = RestClient.create("http://localhost:" + port);

		jdbcTemplate.update("delete from vector_store");
		jdbcTemplate.update("delete from knowledge_source");
		ingestionService.ingest();
	}

	@AfterEach
	void tearDown() {
		// The models are context-scoped beans, so a scripted behaviour would otherwise leak into
		// whichever test ran next.
		primaryModel.reset();
		fallbackModel.reset();
	}

	@Test
	void streamsAnAnswerAsServerSentEvents() {
		primaryModel.set(StubChatModel.answering("Alpha ", "indexes ", "recipes."));

		String body = ask(ARTICHOKE_QUESTION);

		// The wire format itself: named events, one JSON object per data line. A browser reading
		// this with eventsource-parser gets exactly the three event types the contract names.
		assertThat(body).contains("event:token");
		assertThat(body).contains("data:{\"text\":\"Alpha \"}");
		assertThat(body).contains("event:sources");
		assertThat(body).contains("event:done");
		assertThat(body).contains("\"model\":\"primary\"");
	}

	@Test
	void citesThePageTheAnswerCameFrom() {
		primaryModel.set(StubChatModel.answering("Alpha indexes recipes."));

		String body = ask(ARTICHOKE_QUESTION);

		// Retrieval ran against the real vector store, so this url came out of the chunk metadata
		// written at ingestion -- it is not something the test arranged.
		assertThat(body).contains("\"url\":\"/projects/alpha\"");
		assertThat(body).contains("\"title\":\"Alpha case study\"");
	}

	@Test
	void putsTheRetrievedContentInFrontOfTheModel() {
		primaryModel.set(StubChatModel.answering("An answer."));

		ask(ARTICHOKE_QUESTION);

		// The whole feature in one assertion: the model was given this site's content and told to
		// answer from it. "artichoke" appears in exactly one fixture.
		assertThat(primaryModel.current().lastPromptText()).contains("artichoke");
		assertThat(primaryModel.current().lastPromptText()).contains("Answer only from the CONTEXT");
	}

	@Test
	void switchesToTheFallbackAndSaysSo() {
		primaryModel.set(StubChatModel.failingBeforeFirstToken());
		fallbackModel.set(StubChatModel.answering("Fallback ", "answer."));

		String body = ask(ARTICHOKE_QUESTION);

		// The acceptance criterion: a primary failure is survivable and visible. The visitor gets
		// an answer; the page can tell which model wrote it.
		assertThat(body).contains("data:{\"text\":\"Fallback \"}");
		assertThat(body).contains("\"model\":\"fallback\"");
	}

	@Test
	void answersFiveOhThreeWhenNeitherModelCanAnswer() {
		primaryModel.set(StubChatModel.failingBeforeFirstToken());
		fallbackModel.set(StubChatModel.failingBeforeFirstToken());

		// A real status line, not an error event. Nothing was written before the decision, which
		// is the reason the first token is resolved before the response is committed.
		var entity = client.post()
				.uri(CHAT_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(ARTICHOKE_QUESTION)
				.retrieve()
				.onStatus(status -> true, (request, failure) -> {
				})
				.toEntity(String.class);
		assertThat(entity.getStatusCode().value()).isEqualTo(503);
		assertThat(entity.getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(entity.getHeaders().getFirst("Retry-After")).isNotBlank();
		assertThat(entity.getBody()).contains(PROBLEM_BASE + "upstream-unavailable");
	}

	@Test
	void reportsAFailureThatHappensMidStreamInsideTheStream() {
		primaryModel.set(StubChatModel.failingMidStream("Half an ", "answer"));

		String body = ask(ARTICHOKE_QUESTION);

		// By this point the 200 is long gone, so the only honest place left to report the failure
		// is the stream. `done` never arrives, which is how the client knows not to trust it.
		assertThat(body).contains("event:error");
		assertThat(body).contains(PROBLEM_BASE + "upstream-unavailable");
		assertThat(body).doesNotContain("event:done");
	}

	@Test
	void tellsTheModelThereIsNoContextWhenRetrievalComesBackEmpty() {
		primaryModel.set(StubChatModel.answering("I don't have that on the site."));

		// Emptied rather than asked an off-topic question, because the stub embedding model has no
		// semantics to be off-topic *to*: it hashes trigrams, so everything is faintly similar to
		// everything, and the test profile floors the threshold at 0 for exactly that reason.
		// Whether a real threshold rejects a real near-miss is a question about Gemini's vectors,
		// and the only honest way to answer it is the manual eval in chat-eval.md.
		//
		// What is provable here is the code path that matters: no chunks retrieved means the model
		// is told so in as many words, and the sources event still arrives, empty.
		jdbcTemplate.update("delete from vector_store");

		String body = ask("{\"messages\":[{\"role\":\"user\",\"content\":"
				+ "\"What were the quarterly revenue figures for 1987?\"}]}");

		assertThat(body).contains("event:sources");
		assertThat(body).contains("data:{\"sources\":[]}");
		assertThat(primaryModel.current().lastPromptText())
				.contains("No content on this site matched the question");
	}

	@Test
	void rejectsElevenMessagesWithoutCallingAModel() {
		primaryModel.set(StubChatModel.answering("should never run"));

		StringBuilder messages = new StringBuilder();
		for (int i = 0; i < 11; i++) {
			messages.append(i > 0 ? "," : "")
					.append("{\"role\":\"user\",\"content\":\"q").append(i).append("\"}");
		}

		var entity = client.post()
				.uri(CHAT_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"messages\":[" + messages + "]}")
				.retrieve()
				.onStatus(status -> true, (request, failure) -> {
				})
				.toEntity(String.class);

		assertThat(entity.getStatusCode().value()).isEqualTo(400);
		assertThat(entity.getBody()).contains(PROBLEM_BASE + "validation");
		// The point of validating before the service: a rejected request must not cost a token.
		assertThat(primaryModel.current().calls()).isZero();
	}

	@Test
	void carriesTheRateLimitHeadersOnTheStream() {
		primaryModel.set(StubChatModel.answering("An answer."));

		var entity = client.post()
				.uri(CHAT_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(ARTICHOKE_QUESTION)
				.retrieve()
				.toEntity(String.class);

		assertThat(entity.getHeaders().getFirst("RateLimit-Limit")).isNotBlank();
		assertThat(entity.getHeaders().getFirst("RateLimit-Remaining")).isNotBlank();
		assertThat(entity.getHeaders().getContentType())
				.satisfies(type -> assertThat(type.toString()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
	}

	@Test
	void keepsTheConversationsEarlierTurnsInThePrompt() {
		primaryModel.set(StubChatModel.answering("Elixir."));

		ask("""
				{"messages":[
				  {"role":"user","content":"What is Beta?"},
				  {"role":"assistant","content":"The second fixture."},
				  {"role":"user","content":"What is it written in?"}
				]}""");

		// "it" only resolves if the earlier turns reached the model. This is the whole reason the
		// endpoint accepts a conversation rather than a single question.
		assertThat(primaryModel.current().lastPromptText())
				.contains("What is Beta?")
				.contains("The second fixture.")
				.contains("What is it written in?");
	}

	/** Posts a conversation and returns the whole SSE body as text. */
	private String ask(String body) {
		String response = client.post()
				.uri(CHAT_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(String.class);

		assertThat(response).isNotNull();
		return response;
	}

}
