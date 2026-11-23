package dev.saq.portfolio.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.dto.response.StatusHistoryResponse;
import dev.saq.portfolio.dto.response.StatusOverviewResponse;
import dev.saq.portfolio.exception.NotFoundException;
import dev.saq.portfolio.model.StatusWindow;
import dev.saq.portfolio.service.StatusService;
import dev.saq.testsupport.WebInfrastructureTestConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP in, HTTP out: the two JSON shapes the contract promises, the cache header, the window
 * parameter's default and bounds, and the 404, with the service mocked.
 */
@WebMvcTest(StatusController.class)
@Import(WebInfrastructureTestConfig.class)
@ActiveProfiles("test")
class StatusControllerTest {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String STATUS_PATH = "/api/v1/status";

	private static final String HISTORY_PATH = "/api/v1/status/githmind/history";

	private static final Instant CHECKED_AT = Instant.parse("2026-11-22T10:35:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StatusService statusService;

	@Test
	void servesTheOverviewShapeTheContractDocuments() throws Exception {
		given(statusService.getOverview()).willReturn(new StatusOverviewResponse("degraded",
				List.of(new StatusOverviewResponse.ServiceStatus("githmind", "githMind",
						"https://githmind.example.com", "degraded", 2400, 99.82)),
				CHECKED_AT));

		mockMvc.perform(get(STATUS_PATH))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type",
						MediaType.APPLICATION_JSON_VALUE))
				.andExpect(jsonPath("$.overall").value("degraded"))
				.andExpect(jsonPath("$.checkedAt").value("2026-11-22T10:35:00Z"))
				.andExpect(jsonPath("$.services[0].slug").value("githmind"))
				.andExpect(jsonPath("$.services[0].name").value("githMind"))
				.andExpect(jsonPath("$.services[0].url").value("https://githmind.example.com"))
				.andExpect(jsonPath("$.services[0].state").value("degraded"))
				.andExpect(jsonPath("$.services[0].responseTimeMs").value(2400))
				.andExpect(jsonPath("$.services[0].uptime30d").value(99.82));
	}

	@Test
	void answersTwoHundredWithAnEmptyServiceListRatherThanAnError() throws Exception {
		// The configuration the application ships in. The site reads exactly this to decide
		// whether to publish its status page, so it has to be a normal 200.
		given(statusService.getOverview())
				.willReturn(new StatusOverviewResponse("unknown", List.of(), CHECKED_AT));

		mockMvc.perform(get(STATUS_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overall").value("unknown"))
				.andExpect(jsonPath("$.services").isArray())
				.andExpect(jsonPath("$.services").isEmpty());
	}

	@Test
	void letsTheOverviewBeCachedForAMinute() throws Exception {
		given(statusService.getOverview())
				.willReturn(new StatusOverviewResponse("operational", List.of(), CHECKED_AT));

		mockMvc.perform(get(STATUS_PATH))
				.andExpect(header().string("Cache-Control", "max-age=60, public"));
	}

	@Test
	void sendsANullResponseTimeForAServiceThatDidNotAnswer() throws Exception {
		given(statusService.getOverview()).willReturn(new StatusOverviewResponse("down",
				List.of(new StatusOverviewResponse.ServiceStatus("githmind", "githMind",
						"https://githmind.example.com", "down", null, 97.2)),
				CHECKED_AT));

		mockMvc.perform(get(STATUS_PATH))
				.andExpect(jsonPath("$.services[0].responseTimeMs").isEmpty());
	}

	@Test
	void servesTheHistoryShapeTheContractDocuments() throws Exception {
		given(statusService.getHistory("githmind", StatusWindow.LAST_24H))
				.willReturn(new StatusHistoryResponse("githmind", "24h", 99.82,
						List.of(new StatusHistoryResponse.Point(CHECKED_AT, "operational", 182),
								new StatusHistoryResponse.Point(CHECKED_AT.plusSeconds(300),
										"unknown", null))));

		mockMvc.perform(get(HISTORY_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("githmind"))
				.andExpect(jsonPath("$.window").value("24h"))
				.andExpect(jsonPath("$.uptime").value(99.82))
				.andExpect(jsonPath("$.points[0].timestamp").value("2026-11-22T10:35:00Z"))
				.andExpect(jsonPath("$.points[0].state").value("operational"))
				.andExpect(jsonPath("$.points[0].avgResponseTimeMs").value(182))
				.andExpect(jsonPath("$.points[1].state").value("unknown"))
				.andExpect(jsonPath("$.points[1].avgResponseTimeMs").isEmpty());
	}

	@Test
	void defaultsToTheTwentyFourHourWindowLikeTheContract() throws Exception {
		given(statusService.getHistory(anyString(), any()))
				.willReturn(history(StatusWindow.LAST_24H));

		mockMvc.perform(get(HISTORY_PATH)).andExpect(status().isOk());

		then(statusService).should().getHistory("githmind", StatusWindow.LAST_24H);
	}

	@Test
	void acceptsEveryWindowTheContractOffers() throws Exception {
		for (StatusWindow window : StatusWindow.values()) {
			given(statusService.getHistory("githmind", window)).willReturn(history(window));

			mockMvc.perform(get(HISTORY_PATH).param("window", window.toApi()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.window").value(window.toApi()));
		}
	}

	@Test
	void rejectsAWindowTheContractDoesNotOffer() throws Exception {
		mockMvc.perform(get(HISTORY_PATH).param("window", "90d"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type",
						MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));

		then(statusService).should(never()).getHistory(anyString(), any());
	}

	@Test
	void answersFourOhFourForAServiceNobodyMonitors() throws Exception {
		willThrow(new NotFoundException("No monitored service with slug not-monitored"))
				.given(statusService).getHistory("not-monitored", StatusWindow.LAST_24H);

		mockMvc.perform(get("/api/v1/status/not-monitored/history"))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Content-Type",
						MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "not-found"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value(
						"No monitored service with slug not-monitored"));
	}

	@Test
	void letsHistoryBeCachedForAMinuteToo() throws Exception {
		given(statusService.getHistory(anyString(), any()))
				.willReturn(history(StatusWindow.LAST_24H));

		mockMvc.perform(get(HISTORY_PATH))
				.andExpect(header().string("Cache-Control", "max-age=60, public"));
	}

	@Test
	void neverRateLimitsEitherEndpoint() throws Exception {
		// The contract documents no 429 here, and the one moment the page matters is an outage,
		// when traffic spikes. Twenty requests in a row must all be served.
		given(statusService.getOverview())
				.willReturn(new StatusOverviewResponse("operational", List.of(), CHECKED_AT));

		for (int request = 0; request < 20; request++) {
			mockMvc.perform(get(STATUS_PATH).with(remoteAddress()))
					.andExpect(status().isOk());
		}
	}

	private static StatusHistoryResponse history(StatusWindow window) {
		return new StatusHistoryResponse("githmind", window.toApi(), 100.0, List.of());
	}

	/** One fixed address: there is no rate limiter in the way, so it only has to be valid. */
	private static org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress() {
		return request -> {
			request.setRemoteAddr("203.0.113.7");
			return request;
		};
	}

}
