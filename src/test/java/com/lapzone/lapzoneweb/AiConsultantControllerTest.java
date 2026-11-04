package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.controller.AiConsultantController;
import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.dto.ChatRequestDTO;
import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConsultantControllerTest {

    @Test
    void shouldKeepOnlyEightRecentMessagesAndAllowReset() {
        AiConsultantService service = mock(AiConsultantService.class);
        when(service.consult(anyString(), anyList())).thenAnswer(invocation -> "reply-" + invocation.getArgument(0));
        AiConsultantController controller = new AiConsultantController(service);
        MockHttpSession session = new MockHttpSession();

        for (int i = 1; i <= 5; i++) {
            controller.chat(new ChatRequestDTO("question-" + i), session);
        }

        List<ChatMessageDTO> recentHistory =
            (List<ChatMessageDTO>) session.getAttribute("lapzoneAiChatHistory");
        assertEquals(8, recentHistory.size());
        assertEquals("question-2", recentHistory.get(0).text());
        assertEquals("reply-question-5", recentHistory.get(7).text());
        verify(service).consult("question-2",
            List.of(new ChatMessageDTO("user", "question-1"), new ChatMessageDTO("model", "reply-question-1")));

        controller.reset(session);
        assertNull(session.getAttribute("lapzoneAiChatHistory"));
    }
}
