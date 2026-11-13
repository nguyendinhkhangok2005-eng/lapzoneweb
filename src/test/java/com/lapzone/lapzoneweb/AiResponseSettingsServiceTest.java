package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.AiResponseSettingsService;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import(AiResponseSettingsService.class)
class AiResponseSettingsServiceTest {
    @Autowired AiResponseSettingsService service;
    @Autowired AiResponseSettingsRepository settings;
    @Autowired AiResponseSettingsChangeRepository changes;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    User actor;
    @BeforeEach void setup() { actor = login("ADMIN"); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    User login(String role) {
        User u = users.saveAndFlush(new User(null, "Quản trị", UUID.randomUUID()+"@test.local", null, "unused", null, role));
        var principal = new CustomUserDetails(u);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        return u;
    }
    int code(Runnable work) { return assertThrows(ResponseStatusException.class, work::run).getStatusCode().value(); }
    AiResponseSettingsService.Input input(String style, String length, String max, boolean promotions, boolean related, Long version) {
        return new AiResponseSettingsService.Input(style, length, max, promotions, related, version);
    }

    @Test void initialDefaultsAndPreviewNeverPersistOrCallAi() {
        var defaults = service.current();
        assertEquals(AiResponseSettings.Style.FRIENDLY, defaults.style());
        assertEquals(AiResponseSettings.Length.MEDIUM, defaults.responseLength());
        assertEquals(3, defaults.maxRecommendations()); assertFalse(defaults.highlightPromotions()); assertFalse(defaults.suggestRelatedProducts());
        String preview = service.preview(input("PROFESSIONAL", "DETAILED", "5", true, true, null));
        assertTrue(preview.contains("LAPZONE đã đối chiếu")); assertTrue(preview.contains("Giới hạn: 5"));
        assertTrue(preview.contains("không phải tư vấn sản phẩm thật"));
        assertEquals(0, settings.count()); assertEquals(0, changes.count());
    }

    @Test void validatesOnlyAllowedValuesBeforeAnyPersistence() {
        for (var invalid : new AiResponseSettingsService.Input[] {
            input("FAKE", "MEDIUM", "3", false, false, null), input("FRIENDLY", "FAKE", "3", false, false, null),
            input("FRIENDLY", "SHORT", "0", false, false, null), input("FRIENDLY", "SHORT", "6", false, false, null),
            input("FRIENDLY", "SHORT", "3.5", false, false, null), input("<script>", "SHORT", "3", false, false, null)
        }) assertEquals(400, code(() -> service.save(invalid)));
        assertEquals(0, settings.count()); assertEquals(0, changes.count());
    }

    @Test void changesPersistWithAdminIdentityTimeAndOldNewValuesAndResetRestoresDefaults() {
        service.save(input("PROFESSIONAL", "DETAILED", "5", true, true, null));
        entityManager.flush(); entityManager.clear();
        var updated = service.current();
        assertEquals(5, updated.maxRecommendations()); assertTrue(updated.highlightPromotions()); assertTrue(updated.suggestRelatedProducts());
        assertEquals(actor.getId(), updated.updatedById()); assertNotNull(updated.updatedAt());
        var first = changes.findAll().getFirst();
        assertEquals(actor.getId(), first.getAdminId()); assertNotNull(first.getChangedAt());
        assertTrue(first.getBeforeValue().contains("tối đa 3")); assertTrue(first.getAfterValue().contains("tối đa 5"));
        assertFalse(first.toString().contains("GEMINI_API_KEY"));
        assertEquals(409, code(() -> service.save(input("CONCISE", "SHORT", "1", false, false, null))));
        service.reset(updated.version());
        entityManager.flush(); entityManager.clear();
        var reset = service.current();
        assertEquals(AiResponseSettings.Style.FRIENDLY, reset.style());
        assertEquals(AiResponseSettings.Length.MEDIUM, reset.responseLength());
        assertEquals(3, reset.maxRecommendations()); assertFalse(reset.highlightPromotions()); assertFalse(reset.suggestRelatedProducts());
        assertEquals(2, changes.count());
        assertEquals(AiResponseSettingsChange.Action.RESET, serviceHistoryAction());
        assertEquals(actor.getId(), changes.findAll().stream().filter(c -> c.getAction() == AiResponseSettingsChange.Action.RESET).findFirst().orElseThrow().getAdminId());
    }
    AiResponseSettingsChange.Action serviceHistoryAction() {
        return changes.findAll().stream().filter(c -> c.getAction() == AiResponseSettingsChange.Action.RESET).findFirst().orElseThrow().getAction();
    }

    @Test void rejectsNonAdminAndRevokedDatabaseRolesForWritesAndHistory() {
        login("USER");
        assertEquals(403, code(() -> service.save(input("FRIENDLY", "SHORT", "1", false, false, null))));
        assertEquals(403, code(() -> service.reset(null)));
        assertEquals(403, code(() -> service.history(0)));
        SecurityContextHolder.clearContext();
        assertEquals(403, code(() -> service.history(0)));
        var principal = new CustomUserDetails(actor);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        actor.setRole("USER"); users.saveAndFlush(actor);
        assertEquals(403, code(() -> service.save(input("FRIENDLY", "SHORT", "1", false, false, null))));
        assertEquals(0, settings.count());
    }
}
