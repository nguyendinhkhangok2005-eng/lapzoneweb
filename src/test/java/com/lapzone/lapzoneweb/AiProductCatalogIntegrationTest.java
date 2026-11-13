package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({AiConversationService.class, AiProductCatalogIntegrationTest.Config.class})
class AiProductCatalogIntegrationTest {
    @TestConfiguration static class Config {
        @Bean RestTemplate aiTestTransport() { return mock(RestTemplate.class); }
        @Bean AiConsultantService aiConsultantService(ProductRepository products, RestTemplate transport) {
            return new AiConsultantService(products, "test-key", transport, new ObjectMapper());
        }
    }
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired AiMessageRepository messages;
    @Autowired EntityManager em;
    @Autowired AiConsultantService consultant;
    @Autowired AiConversationService conversations;
    @Autowired RestTemplate transport;

    @BeforeEach void resetTransport() { reset(transport); }

    Product saveProduct() {
        Category c = new Category(); c.setName("Gaming " + UUID.randomUUID()); categories.save(c);
        Product p = new Product(); p.setName("Acer Gaming"); p.setCategory(c); p.setStock(3); p.setPrice(18_000_000d);
        ProductDetail d = new ProductDetail(); d.setProduct(p); d.setCpu("Intel Core i7");
        d.setGpu("RTX 4060"); d.setRam("16GB DDR5"); d.setStorage("1TB SSD"); p.setProductDetail(d);
        ProductImage first = new ProductImage(); first.setProduct(p); first.setImageUrl("assets/first.jpg");
        ProductImage second = new ProductImage(); second.setProduct(p); second.setImageUrl("second.jpg");
        p.setImages(List.of(first, second)); return products.saveAndFlush(p);
    }

    ResponseEntity<String> response(Long id) throws Exception {
        String selection = new ObjectMapper().writeValueAsString(Map.of("message", "untrusted price 1đ", "productIds", List.of(id)));
        return ResponseEntity.ok(new ObjectMapper().writeValueAsString(Map.of("candidates", List.of(
            Map.of("content", Map.of("parts", List.of(Map.of("text", selection))))))));
    }

    @Test void repositoryReadsRealDetailsAndGalleryWithOneCardPerProduct() {
        Product p = saveProduct();
        List<AiProductDTO> cards = consultant.currentProducts(List.of(p.getId()));
        assertEquals(1, cards.size()); AiProductDTO card = cards.getFirst();
        assertEquals("/assets/first.jpg", card.imageUrl());
        assertEquals("1TB SSD", card.specifications().get("Ổ cứng"));
        assertEquals("/product_detail?id=" + p.getId(), card.detailUrl());
        assertEquals(PromotionService.getDiscountedPrice(p), card.price());
    }

    @Test void sqlRevalidationRejectsStockChangeEvenWithPreviouslyLoadedJpaEntity() throws Exception {
        Product p = saveProduct();
        when(transport.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenAnswer(call -> {
            em.createQuery("update Product p set p.stock = 0 where p.id = :id").setParameter("id", p.getId()).executeUpdate();
            return response(p.getId());
        });
        AiReplyDTO reply = consultant.consultWithProducts("gaming RAM 16GB dưới 20 triệu", List.of());
        assertEquals(3, p.getStock(), "The original managed entity is deliberately stale");
        assertTrue(reply.products().isEmpty()); assertTrue(reply.reply().contains("được xác thực"));
    }

    @Test void selectedReferencesPersistAndReopenWithCurrentFactsInOnlyTheirConversation() throws Exception {
        Product p = saveProduct();
        when(transport.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response(p.getId()));
        var owner = new AiConversationService.Access(null, UUID.randomUUID().toString());
        String first = conversations.create(owner, "Gaming").id();
        String second = conversations.create(owner, "Separate").id();
        AiReplyDTO live = conversations.chatWithProducts(first, owner, "gaming RAM 16GB dưới 20 triệu");
        assertNotNull(live.messageId());
        assertEquals(live.messageId(), messages.findByConversationIdOrderByIdAsc(first).getLast().getId());
        assertEquals(p.getId(), live.products().getFirst().id());
        assertEquals(p.getId().toString(), messages.findByConversationIdOrderByIdAsc(first).getLast().getProductIds());
        em.flush(); em.clear();
        em.createQuery("update Product p set p.stock = 0, p.price = 22000000 where p.id = :id").setParameter("id", p.getId()).executeUpdate();
        var history = conversations.retrieve(first, owner);
        assertEquals(live.messageId(), history.messages().getLast().id());
        AiProductDTO refreshed = history.messages().getLast().products().getFirst();
        assertFalse(refreshed.available()); assertEquals(0, refreshed.stock());
        assertTrue(refreshed.price() > live.products().getFirst().price());
        assertTrue(conversations.retrieve(second, owner).messages().isEmpty());
        assertEquals(2, history.messages().size());
        assertEquals(live.reply(), history.messages().getLast().text());
    }

    @Test void candidateScanFindsMatchesBeyondFirstBatchWithoutHydratingTheCatalog() throws Exception {
        Category c = new Category(); c.setName("Danh mục kiểm thử"); categories.saveAndFlush(c);
        Product last = null;
        for (int i = 0; i < 105; i++) {
            Product p = new Product(); p.setName("Laptop kiểm thử " + i); p.setCategory(c); p.setStock(3); p.setPrice(10_000_000d);
            ProductDetail detail = new ProductDetail(); detail.setProduct(p); detail.setRam(i == 104 ? "32GB" : "8GB");
            p.setProductDetail(detail); last = products.save(p);
        }
        em.flush(); Long matchId = Objects.requireNonNull(last).getId(); em.clear();
        when(transport.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response(matchId));
        var statistics = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled(); statistics.setStatisticsEnabled(true); statistics.clear();
        try {
            var reply = consultant.consultWithProducts("Laptop RAM 32GB dưới 20 triệu", List.of());
            assertEquals(List.of(matchId), reply.products().stream().map(p -> p.id()).toList());
            assertEquals(0, statistics.getEntityLoadCount(), "Candidate and validation queries must be scalar, without managed catalog entities");
            assertEquals(3, statistics.getPrepareStatementCount(), "Two bounded candidate batches and one current-product validation query");
            verify(transport, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        } finally { statistics.setStatisticsEnabled(enabled); }
    }

    @Test void reopeningRepeatedProductReferencesUsesOneValidationQueryForTheWholeHistory() throws Exception {
        Product p = saveProduct();
        when(transport.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(response(p.getId()));
        var owner = new AiConversationService.Access(null, UUID.randomUUID().toString());
        String id = conversations.create(owner, "Lịch sử gợi ý").id();
        for (int i = 0; i < 3; i++) conversations.chatWithProducts(id, owner, "gaming RAM 16GB dưới 20 triệu");
        em.flush(); em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled(); statistics.setStatisticsEnabled(true); statistics.clear();
        try {
            var history = conversations.retrieve(id, owner);
            assertEquals(6, history.messages().size());
            assertEquals(3, history.messages().stream().filter(m -> !m.products().isEmpty()).count());
            assertEquals(3, statistics.getPrepareStatementCount(), "Ownership, message history and one shared product-validation query");
        } finally { statistics.setStatisticsEnabled(enabled); }
    }
}
