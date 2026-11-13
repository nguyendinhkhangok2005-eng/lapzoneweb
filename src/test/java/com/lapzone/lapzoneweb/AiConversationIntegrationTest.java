package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.model.service.AiConversationService.Access;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the application's configured MySQL database; all test data rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiConversationIntegrationTest {
    @Autowired AiConversationService service;
    @Autowired AiConversationRepository conversations;
    @Autowired AiMessageRepository messages;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @MockitoBean AiConsultantService consultant;

    @Test
    void httpApiUsesExistingAuthenticationAndRejectsOtherUsersAndGuests() throws Exception {
        Access owner = owner();
        String id = service.create(owner, "Private").id();
        MockHttpSession session = authenticatedSession(owner);
        when(consultant.consultWithProducts(anyString(), anyList())).thenReturn(new AiReplyDTO("Recommended laptop"));
        mvc.perform(post("/api/ai/chat").session(session).contentType("application/json")
                .content("{\"message\":\"laptop\",\"conversationId\":\"" + id + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(id))
            .andExpect(jsonPath("$.reply").value("Recommended laptop"));
        mvc.perform(get("/api/ai/conversations/" + id).session(authenticatedSession(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[0].conversationId").value(id));

        Access stranger = owner();
        for (MockHttpSession denied : List.of(authenticatedSession(stranger), new MockHttpSession())) {
            mvc.perform(get("/api/ai/conversations/" + id).session(denied)).andExpect(status().isNotFound());
            mvc.perform(patch("/api/ai/conversations/" + id).session(denied).contentType("application/json")
                .content("{\"title\":\"stolen\"}")).andExpect(status().isNotFound());
            mvc.perform(delete("/api/ai/conversations/" + id).session(denied)).andExpect(status().isNotFound());
            mvc.perform(post("/api/chat/reset").session(denied).param("conversationId", id))
                .andExpect(status().isNotFound());
            mvc.perform(post("/api/ai/chat").session(denied).contentType("application/json")
                .content("{\"message\":\"secret\",\"conversationId\":\"" + id + "\"}"))
                .andExpect(status().isNotFound());
        }
        verify(consultant, times(1)).consultWithProducts(anyString(), anyList());
    }

    @Test
    void guestHttpLifecycleAndLegacyDefaultsStaySeparateAcrossLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(consultant.consultWithProducts(anyString(), anyList())).thenReturn(new AiReplyDTO("reply"));
        mvc.perform(post("/api/ai/conversations").session(session).contentType("application/json")
                .content("{\"title\":\"Guest title\"}"))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.title").value("Guest title"));
        mvc.perform(get("/api/ai/conversations").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/ai/chat").session(session).contentType("application/json")
                .content("{\"message\":\"guest question\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reply").value("reply"));
        String guestId = (String) session.getAttribute("lapzoneAiDefaultConversation:guest");
        mvc.perform(patch("/api/ai/conversations/" + guestId).session(session).contentType("application/json")
                .content("{\"title\":\"Renamed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed"));

        Access owner = owner();
        MockHttpSession login = authenticatedSession(owner);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            login.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        mvc.perform(post("/api/ai/chat").session(session).contentType("application/json")
                .content("{\"message\":\"owner question\"}"))
            .andExpect(status().isOk());
        verify(consultant).consultWithProducts("owner question", List.of());
        mvc.perform(get("/api/ai/conversations/" + guestId).session(session)).andExpect(status().isNotFound());
        String ownerId = (String) session.getAttribute("lapzoneAiDefaultConversation:" + owner.ownerId());
        mvc.perform(post("/api/chat/reset").session(session).param("conversationId", ownerId))
            .andExpect(status().isOk());
        mvc.perform(get("/api/ai/conversations/" + ownerId).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(delete("/api/ai/conversations/" + ownerId).session(session)).andExpect(status().isNoContent());
        assertNull(session.getAttribute("lapzoneAiDefaultConversation:" + owner.ownerId()));
    }

    private MockHttpSession authenticatedSession(Access access) {
        CustomUserDetails principal = new CustomUserDetails(users.findById(access.ownerId()).orElseThrow());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test
    void persistsSeparateContextsAndAllMessagesBeyondTheTwelveMessageWindow() {
        Access access = guest();
        String first = service.create(access, "Gaming").id();
        String second = service.create(access, "Office").id();
        when(consultant.consultWithProducts(anyString(), anyList())).thenAnswer(call -> new AiReplyDTO("reply-" + call.getArgument(0)));

        for (int i = 1; i <= 8; i++) service.chat(first, access, "question-" + i);
        service.chat(second, access, "independent");
        verify(consultant).consultWithProducts("independent", List.of());
        List<ChatMessageDTO> expected = new ArrayList<>();
        for (int i = 2; i <= 7; i++) {
            expected.add(new ChatMessageDTO("user", "question-" + i));
            expected.add(new ChatMessageDTO("model", "reply-question-" + i));
        }
        verify(consultant).consultWithProducts(eq("question-8"), argThat(actual -> actual.stream().map(m -> new ChatMessageDTO(m.role(), m.text())).toList().equals(expected)));

        entityManager.flush();
        entityManager.clear();
        ConversationDTO.Detail persisted = service.retrieve(first, access);
        assertEquals(16, persisted.messages().size());
        assertEquals("question-1", persisted.messages().getFirst().text());
        assertTrue(persisted.messages().stream().allMatch(m -> first.equals(m.conversationId()) && m.createdAt() != null));
        assertEquals(2, service.retrieve(second, access).messages().size());
    }

    @Test
    void ownerCanResumeAcrossSessionsAndOtherOwnersAndGuestsCannotAccessAnyOperation() {
        Access owner = owner();
        Access otherOwner = owner();
        Access guest = guest();
        String id = service.create(owner, null).id();
        entityManager.flush();
        entityManager.clear();
        assertEquals(id, service.retrieve(id, new Access(owner.ownerId(), null)).conversation().id());
        assertEquals(1, service.list(owner, 0, 20).size());

        for (Access stranger : List.of(otherOwner, guest)) {
            assertTrue(service.list(stranger, 0, 20).isEmpty());
            assertNotFound(() -> service.retrieve(id, stranger));
            assertNotFound(() -> service.rename(id, stranger, "stolen"));
            assertNotFound(() -> service.delete(id, stranger));
            assertNotFound(() -> service.reset(id, stranger));
            assertNotFound(() -> service.chat(id, stranger, "secret?"));
        }
        verifyNoInteractions(consultant);
        assertTrue(service.retrieve(id, owner).messages().isEmpty());
    }

    @Test
    void guestsAreIsolatedAndAuthenticatedUsersDoNotInheritGuestHistory() {
        Access guest = guest();
        String id = service.create(guest, "Private guest").id();
        assertNotFound(() -> service.retrieve(id, guest()));
        assertNotFound(() -> service.retrieve(id, owner()));
        assertEquals(id, service.retrieve(id, guest).conversation().id());
    }

    @Test
    void renameResetAndDeleteAffectOnlyTheSelectedConversation() {
        Access access = guest();
        String first = service.create(access, null).id();
        String second = service.create(access, "Keep").id();
        when(consultant.consultWithProducts(anyString(), anyList())).thenReturn(new AiReplyDTO("reply"));
        service.chat(first, access, "first");
        service.chat(second, access, "second");
        assertEquals("Renamed", service.rename(first, access, " Renamed ").title());
        service.reset(first, access);
        assertTrue(service.retrieve(first, access).messages().isEmpty());
        assertEquals(2, service.retrieve(second, access).messages().size());
        service.delete(second, access);
        entityManager.flush();
        entityManager.clear();
        assertNotFound(() -> service.retrieve(second, access));
        assertTrue(messages.findByConversationIdOrderByIdAsc(second).isEmpty());
        assertEquals(1, service.list(access, 0, 20).size());
    }

    @Test
    void rejectsInvalidTitlesPaginationAndOwnerScopes() {
        Access access = guest();
        for (String title : List.of("", " ", "x".repeat(121), "bad\nname")) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.create(access, title)).getStatusCode().value());
        }
        assertThrows(ResponseStatusException.class, () -> service.list(access, -1, 20));
        assertThrows(ResponseStatusException.class, () -> service.list(access, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> new Access(null, null));
        assertThrows(IllegalArgumentException.class, () -> new Access(1L, "guest"));
        assertNotFound(() -> service.chat("missing", access, "question"));
    }

    private Access owner() {
        String unique = UUID.randomUUID().toString();
        User user = users.saveAndFlush(new User(null, "Conversation test", unique + "@example.test",
            "0000000000", "unused-test-password", null, "USER"));
        return new Access(user.getId(), null);
    }

    private Access guest() { return new Access(null, UUID.randomUUID().toString()); }

    private void assertNotFound(Runnable action) {
        assertEquals(404, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }
}
