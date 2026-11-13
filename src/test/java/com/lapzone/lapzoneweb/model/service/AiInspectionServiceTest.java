package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({AiInspectionRunStore.class, AdminAiInspectionService.class, AiRecommendationValidator.class})
class AiInspectionServiceTest {
    @Autowired AdminAiInspectionService inspector;
    @Autowired AiInspectionRunStore store;
    @Autowired AiInspectionRunRepository runs;
    @Autowired UserRepository users;
    @Autowired AiConversationRepository conversations;
    @Autowired AiMessageRepository messages;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean AiConsultantService consultant;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @BeforeEach void setup() { login("ADMIN"); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    void login(String role) {
        User u = users.saveAndFlush(new User(null, "Người kiểm tra", UUID.randomUUID()+"@test.local", null, "unused", null, role));
        var p = new CustomUserDetails(u); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
    @Test void reusesConsultationOnceStoresImmutableResultsAndNeverCreatesCustomerConversationsOrChangesProducts() {
        Category c = new Category(); c.setName("Gaming"); categories.saveAndFlush(c);
        Product p = new Product(); p.setName("Laptop"); p.setCategory(c); p.setPrice(20_000_000d); p.setStock(4); p.setImage("assets/real.jpg"); products.saveAndFlush(p);
        AiProductDTO product = new AiProductDTO(p.getId(), p.getName(), PromotionService.getDiscountedPrice(p), "/assets/real.jpg", Map.of(), 4, true, "/product_detail?id="+p.getId());
        when(consultant.consultWithProducts(anyString(), eq(List.of()))).thenReturn(new AiReplyDTO("Phản hồi được kiểm tra", List.of(product)));
        String token = UUID.randomUUID().toString();
        Long id = inspector.run("Laptop dưới 25 triệu", "CUSTOM", token);
        assertEquals(id, inspector.run("Laptop dưới 25 triệu", "CUSTOM", token));
        var detail = store.get(id); store.get(id); store.list(0);
        assertEquals("Đã hoàn tất", detail.run().state()); assertNotEquals("Chưa hoàn tất", detail.run().finishedAt());
        assertEquals("Phản hồi được kiểm tra", detail.result().reply());
        assertEquals(AiRecommendationValidator.Status.PASS, detail.result().products().getFirst().checks().stream().filter(x -> x.label().equals("Giá bán")).findFirst().orElseThrow().status());
        assertEquals(1, runs.count()); assertEquals(0, conversations.count()); assertEquals(0, messages.count());
        assertEquals(20_000_000d, products.findById(p.getId()).orElseThrow().getPrice());
        assertEquals(4, products.findById(p.getId()).orElseThrow().getStock());
        verify(consultant, times(1)).consultWithProducts("Laptop dưới 25 triệu", List.of());
        assertEquals(detail.result(), store.get(id).result());
    }
    @Test void failuresAreSavedWithoutLeakingProviderErrorsOrRetrying() {
        when(consultant.consultWithProducts(anyString(), anyList())).thenThrow(new IllegalStateException("SECRET provider token"));
        Long id = inspector.run("Laptop", "CUSTOM", UUID.randomUUID().toString());
        assertEquals("Không hoàn tất", store.get(id).run().state());
        assertFalse(store.get(id).error().contains("SECRET")); assertNull(store.get(id).result());
        assertEquals(0, conversations.count()); verify(consultant, times(1)).consultWithProducts("Laptop", List.of());
    }
    @Test void readsFreshDatabaseFactsEvenWithStaleManagedProductsAndRetainsTheAuditSnapshot() {
        Category c = new Category(); c.setName("Gaming"); categories.saveAndFlush(c);
        Product p = new Product(); p.setName("Laptop"); p.setCategory(c); p.setPrice(18_000_000d); p.setStock(3); p.setImage("assets/real.jpg"); products.saveAndFlush(p);
        AiProductDTO stale = new AiProductDTO(p.getId(), p.getName(), PromotionService.getDiscountedPrice(p), "/assets/real.jpg", Map.of(), 3, true, "/product_detail?id="+p.getId());
        when(consultant.consultWithProducts(anyString(), anyList())).thenAnswer(call -> {
            entityManager.createQuery("update Product p set p.price = 50000000, p.stock = 0 where p.id = :id").setParameter("id", p.getId()).executeUpdate();
            return new AiReplyDTO("Phản hồi cũ", List.of(stale));
        });
        Long id = inspector.run("Laptop dưới 20 triệu", "CUSTOM", UUID.randomUUID().toString());
        assertEquals(18_000_000d, p.getPrice(), "The managed instance remains stale on purpose");
        var saved = store.get(id).result();
        for (String label : List.of("Giá bán", "Ngân sách", "Trạng thái tồn kho", "Khả năng mua hiện tại"))
            assertEquals(AiRecommendationValidator.Status.FAIL, saved.products().getFirst().checks().stream().filter(check -> check.label().equals(label)).findFirst().orElseThrow().status());
        entityManager.createQuery("update Product p set p.price = 1000000, p.stock = 10 where p.id = :id").setParameter("id", p.getId()).executeUpdate();
        assertEquals(saved, store.get(id).result(), "Opening a saved run must not rewrite its original evidence");
    }
    @Test void blocksInvalidQuestionsUnauthorizedUsersAndRapidSecondTestsBeforeCallingAi() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> inspector.run(" ", "CUSTOM", UUID.randomUUID().toString())).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> inspector.run("Laptop", "CUSTOM", "bad-token")).getStatusCode().value());
        store.reserve("Laptop", "CUSTOM", UUID.randomUUID().toString());
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> inspector.run("Laptop", "CUSTOM", UUID.randomUUID().toString())).getStatusCode().value());
        login("USER");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> inspector.run("Laptop", "CUSTOM", UUID.randomUUID().toString())).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> store.list(0)).getStatusCode().value());
        verifyNoInteractions(consultant);
    }
    @Test void limitsHourlyReservationsAndRetainsDuplicateResultWithoutNewUsage() {
        class MutableClock extends Clock {
            Instant now = Instant.parse("2026-11-12T12:00:00Z");
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        }
        MutableClock clock = new MutableClock();
        AiInspectionRunStore limited = new AiInspectionRunStore(runs, users, clock);
        String firstToken = UUID.randomUUID().toString();
        Long first = null;
        for (int i = 0; i < 10; i++) {
            var reservation = limited.reserve("Laptop", "CUSTOM", i == 0 ? firstToken : UUID.randomUUID().toString());
            if (i == 0) first = reservation.id();
            limited.complete(reservation.id(), new AiRecommendationValidator.Result("Phản hồi", List.of(), List.of()));
            clock.now = clock.now.plusSeconds(31);
        }
        assertEquals(first, limited.reserve("Laptop", "CUSTOM", firstToken).id());
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> limited.reserve("Laptop", "CUSTOM", UUID.randomUUID().toString())).getStatusCode().value());
        assertEquals(10, runs.count()); verifyNoInteractions(consultant);
    }
    @Test void predefinedCasesAreExplicitAndCanOnlyBeRunOneAtATime() {
        assertEquals(5, AdminAiInspectionService.Preset.values().length);
        when(consultant.consultWithProducts(anyString(), anyList())).thenReturn(new AiReplyDTO("Không có sản phẩm"));
        var preset = AdminAiInspectionService.Preset.GAMING;
        Long id = inspector.run(preset.getQuestion(), preset.name(), UUID.randomUUID().toString());
        assertEquals("Chơi game", store.get(id).run().preset());
        assertTrue(store.get(id).result().overallChecks().stream().allMatch(x -> x.status() == AiRecommendationValidator.Status.NOT_VERIFIED));
        verify(consultant, times(1)).consultWithProducts(preset.getQuestion(), List.of());
    }
}
