package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AiConsultantController;
import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.dto.ChatRequestDTO;
import com.lapzone.lapzoneweb.model.service.AiChatRateLimiter;
import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiConsultantControllerTest {

    @Test
    void shouldKeepOnlySixRecentTurnsAndAllowReset() {
        AiConsultantService service = mock(AiConsultantService.class);
        when(service.consult(anyString(), anyList())).thenReturn("reply");
        AiConsultantController controller = controller(service, new MutableClock());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("lapzoneAiChatHistory", historyFrom(1, 8));

        send(controller, "new question", session, "192.0.2.1");

        List<ChatMessageDTO> recentHistory =
            (List<ChatMessageDTO>) session.getAttribute("lapzoneAiChatHistory");
        assertEquals(12, recentHistory.size());
        assertEquals("question-4", recentHistory.get(0).text());
        assertEquals("reply-question-8", recentHistory.get(9).text());
        verify(service).consult("new question", historyFrom(3, 8));

        controller.reset(session);
        assertNull(session.getAttribute("lapzoneAiChatHistory"));
    }

    @Test
    void shouldRejectTheSeventhRequestAndAllowItAfterTheMinuteWindow() {
        AiConsultantService service = mock(AiConsultantService.class);
        when(service.consult(anyString(), anyList())).thenReturn("reply");
        MutableClock clock = new MutableClock();
        AiConsultantController controller = controller(service, clock);
        MockHttpSession session = new MockHttpSession();

        for (int i = 0; i < 6; i++) {
            assertEquals(HttpStatus.OK, send(controller, "question", session, "192.0.2.1").getStatusCode());
        }
        ResponseEntity<Map<String, String>> limited =
            send(controller, "question", session, "192.0.2.1");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatusCode());
        assertTrue(limited.getBody().get("reply").contains("thử lại sau"));
        assertTrue(limited.getBody().get("reply").contains("giây"));

        clock.advance(Duration.ofMinutes(1));
        assertEquals(HttpStatus.OK,
            send(controller, "question", session, "192.0.2.1").getStatusCode());
    }

    @Test
    void shouldCountRequestsPerRemoteIpAndIgnoreForwardedFor() {
        AiConsultantService service = mock(AiConsultantService.class);
        when(service.consult(anyString(), anyList())).thenReturn("reply");
        AiConsultantController controller = controller(service, new MutableClock());
        MockHttpSession session = new MockHttpSession();

        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest request = request("192.0.2.10");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            assertEquals(HttpStatus.OK,
                controller.chat(new ChatRequestDTO("question"), session, request).getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
            send(controller, "question", session, "192.0.2.10").getStatusCode());
        assertEquals(HttpStatus.OK,
            send(controller, "question", session, "192.0.2.11").getStatusCode());
    }

    @Test
    void shouldRejectQuestionsLongerThanFiveHundredCharactersWithoutCallingService() {
        AiConsultantService service = mock(AiConsultantService.class);
        AiConsultantController controller = controller(service, new MutableClock());

        ResponseEntity<Map<String, String>> response =
            send(controller, "x".repeat(501), new MockHttpSession(), "192.0.2.1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("reply").contains("500 ký tự"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRemoveControlCharactersFromQuestionAndHistory() {
        AiConsultantService service = mock(AiConsultantService.class);
        when(service.consult(anyString(), anyList())).thenReturn("reply");
        AiConsultantController controller = controller(service, new MutableClock());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("lapzoneAiChatHistory",
            List.of(new ChatMessageDTO("user", "old\u0000question")));

        send(controller, "new\u0007question", session, "192.0.2.1");

        verify(service).consult("newquestion", List.of(new ChatMessageDTO("user", "oldquestion")));
    }

    @Test
    void shouldEnforceTheGlobalPerMinuteLimit() {
        AiConsultantService service = mock(AiConsultantService.class);
        when(service.consult(anyString(), anyList())).thenReturn("reply");
        AiChatRateLimiter rateLimiter = new AiChatRateLimiter(10, 60, 2, new MutableClock());
        AiConsultantController controller = new AiConsultantController(service, rateLimiter);

        assertEquals(HttpStatus.OK,
            send(controller, "one", new MockHttpSession(), "192.0.2.1").getStatusCode());
        assertEquals(HttpStatus.OK,
            send(controller, "two", new MockHttpSession(), "192.0.2.2").getStatusCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
            send(controller, "three", new MockHttpSession(), "192.0.2.3").getStatusCode());
    }

    @Test
    void shouldEnforceThePerIpDailyLimitAfterTheMinuteWindowExpires() {
        AiConsultantService service = mock(AiConsultantService.class);
        when(service.consult(anyString(), anyList())).thenReturn("reply");
        MutableClock clock = new MutableClock();
        AiChatRateLimiter rateLimiter = new AiChatRateLimiter(10, 2, 30, clock);
        AiConsultantController controller = new AiConsultantController(service, rateLimiter);

        assertEquals(HttpStatus.OK,
            send(controller, "one", new MockHttpSession(), "192.0.2.1").getStatusCode());
        assertEquals(HttpStatus.OK,
            send(controller, "two", new MockHttpSession(), "192.0.2.1").getStatusCode());
        clock.advance(Duration.ofMinutes(1));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
            send(controller, "three", new MockHttpSession(), "192.0.2.1").getStatusCode());
    }

    private AiConsultantController controller(AiConsultantService service, Clock clock) {
        return new AiConsultantController(service, new AiChatRateLimiter(6, 60, 30, clock));
    }

    private ResponseEntity<Map<String, String>> send(AiConsultantController controller, String message,
                                                      MockHttpSession session, String ipAddress) {
        return controller.chat(new ChatRequestDTO(message), session, request(ipAddress));
    }

    private MockHttpServletRequest request(String ipAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ipAddress);
        return request;
    }

    private List<ChatMessageDTO> historyFrom(int firstQuestion, int lastQuestion) {
        List<ChatMessageDTO> history = new ArrayList<>();
        for (int i = firstQuestion; i <= lastQuestion; i++) {
            history.add(new ChatMessageDTO("user", "question-" + i));
            history.add(new ChatMessageDTO("model", "reply-question-" + i));
        }
        return history;
    }

    private static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
