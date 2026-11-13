package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({AiResponseSettingsService.class, AiConversationService.class, AiResponseSettingsIntegrationTest.Config.class})
class AiResponseSettingsIntegrationTest {
    @TestConfiguration static class Config {
        @Bean RestTemplate transport() { return mock(RestTemplate.class); }
        @Bean AiConsultantService consultant(ProductRepository products, RestTemplate transport, AiResponseSettingsService settings) {
            return new AiConsultantService(products, "test-key", transport, new ObjectMapper(), null, settings);
        }
    }
    @Autowired AiConsultantService consultant;
    @Autowired AiConversationService conversations;
    @Autowired AiResponseSettingsService settings;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired RestTemplate transport;
    @MockitoBean org.springframework.cache.CacheManager cacheManager;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    Product product(Category category, String name) {
        Product p = new Product(); p.setName(name); p.setCategory(category); p.setPrice(16_000_000d); p.setStock(3); p.setImage("assets/real.jpg");
        ProductDetail d = new ProductDetail(); d.setProduct(p); d.setCpu("Intel Core i7"); d.setRam("16GB DDR5"); d.setStorage("512GB SSD"); d.setGpu("RTX 4050"); p.setProductDetail(d);
        return products.saveAndFlush(p);
    }
    @Test void persistedSettingsImmediatelyAffectNewRepliesAndResetRestoresExistingDefaults() throws Exception {
        User admin = users.saveAndFlush(new User(null, "Quản trị", UUID.randomUUID()+"@test.local", null, "unused", null, "ADMIN"));
        var principal = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        Category c = new Category(); c.setName("Gaming"); categories.saveAndFlush(c);
        Product a = product(c, "Laptop Gaming A"), b = product(c, "Laptop Gaming B");
        String selected = new ObjectMapper().writeValueAsString(Map.of("message", "ignored", "productIds", List.of(a.getId(), b.getId())));
        String body = new ObjectMapper().writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", selected)))))));
        when(transport.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok(body));
        String question = "Tìm laptop gaming dưới 20 triệu";
        AiReplyDTO before = consultant.consultWithProducts(question, List.of());
        assertEquals(2, before.products().size()); assertTrue(before.reply().startsWith("Các sản phẩm phù hợp"));
        settings.save(new AiResponseSettingsService.Input("PROFESSIONAL", "SHORT", "1", true, false, null));
        em.flush(); em.clear();
        AiReplyDTO after = consultant.consultWithProducts(question, List.of());
        assertEquals(1, after.products().size()); assertTrue(after.reply().startsWith("LAPZONE đã đối chiếu"));
        assertEquals(PromotionService.getDiscountedPrice(a), after.products().getFirst().price());
        assertEquals(PromotionService.getDiscountPercent(a), after.products().getFirst().promotionPercent());
        var access = new AiConversationService.Access(null, UUID.randomUUID().toString());
        String conversationId = conversations.create(access, "Cấu hình tiếp theo").id();
        AiReplyDTO saved = conversations.chatWithProducts(conversationId, access, question);
        assertEquals(1, saved.products().size());
        var history = conversations.retrieve(conversationId, access).messages();
        assertEquals(2, history.size()); assertEquals(saved.reply(), history.getLast().text());
        assertEquals(PromotionService.getDiscountPercent(a), history.getLast().products().getFirst().promotionPercent());
        settings.reset(settings.current().version());
        em.flush(); em.clear();
        AiReplyDTO restored = consultant.consultWithProducts(question, List.of());
        assertEquals(2, restored.products().size()); assertTrue(restored.reply().startsWith("Các sản phẩm phù hợp"));
        assertEquals(0, restored.products().getFirst().promotionPercent());
        assertEquals(2, products.count());
        verify(transport, times(4)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }
}
