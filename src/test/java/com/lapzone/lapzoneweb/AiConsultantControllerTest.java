package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AiConsultantController;
import com.lapzone.lapzoneweb.model.dto.ChatRequestDTO;
import com.lapzone.lapzoneweb.model.service.AiChatRateLimiter;
import com.lapzone.lapzoneweb.model.service.AiConversationService;
import com.lapzone.lapzoneweb.model.dto.ConversationDTO;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiConsultantControllerTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReuseLegacyDefaultAndRotateOnResetWithoutDeletingHistory() {
        AiConversationService service = mock(AiConversationService.class);
        stubChat(service);
        AiConsultantController controller = controller(service, new MutableClock());
        MockHttpSession session = new MockHttpSession();
        assertEquals("default-id", send(controller, "first", session, "192.0.2.1").getBody().get("conversationId"));
        send(controller, "second", session, "192.0.2.1");
        verify(service, org.mockito.Mockito.times(1)).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull());
        controller.reset(session, null);
        send(controller, "third", session, "192.0.2.1");
        verify(service, org.mockito.Mockito.times(2)).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull());
        verify(service, org.mockito.Mockito.never()).reset(anyString(), org.mockito.ArgumentMatchers.any());
    }
    @Test
    void shouldRejectTheSeventhRequestAndAllowItAfterTheMinuteWindow() {
        AiConversationService service = mock(AiConversationService.class);
        stubChat(service);
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
        AiConversationService service = mock(AiConversationService.class);
        stubChat(service);
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
        AiConversationService service = mock(AiConversationService.class);
        AiConsultantController controller = controller(service, new MutableClock());

        ResponseEntity<Map<String, String>> response =
            send(controller, "x".repeat(501), new MockHttpSession(), "192.0.2.1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("reply").contains("500 ký tự"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRemoveControlCharactersAndRouteExplicitConversation() {
        AiConversationService service = mock(AiConversationService.class);
        stubChat(service);
        AiConsultantController controller = controller(service, new MutableClock());
        MockHttpSession session = new MockHttpSession();
        controller.chat(new ChatRequestDTO("new\u0007question", "chosen-id"), session, request("192.0.2.1"));
        verify(service).chat(org.mockito.ArgumentMatchers.eq("chosen-id"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("newquestion"));
        verify(service, org.mockito.Mockito.never()).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
    @Test
    void shouldEnforceTheGlobalPerMinuteLimit() {
        AiConversationService service = mock(AiConversationService.class);
        stubChat(service);
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
        AiConversationService service = mock(AiConversationService.class);
        stubChat(service);
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

    private AiConsultantController controller(AiConversationService service, Clock clock) {
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

    private void stubChat(AiConversationService service) {
        when(service.chat(anyString(), org.mockito.ArgumentMatchers.any(), anyString())).thenReturn("reply");
        when(service.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(new ConversationDTO("default-id", "New", Instant.now(), Instant.now()));
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
