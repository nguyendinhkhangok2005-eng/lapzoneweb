package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiResponseSettingsConsultationTest {
    final ProductRepository products = mock(ProductRepository.class);
    final RestTemplate transport = mock(RestTemplate.class);
    final AiResponseSettingsService settings = mock(AiResponseSettingsService.class);
    final AiConsultantService consultant = new AiConsultantService(products, "test-key", transport, new ObjectMapper(), null, settings);
    Product product(long id) {
        Product p = new Product(); p.setId(id); p.setName("Laptop Gaming " + id); p.setPrice(15_000_000d); p.setStock(3);
        Category c = new Category(); c.setName("Gaming"); p.setCategory(c);
        ProductDetail d = new ProductDetail(); d.setCpu("Intel Core i7"); d.setGpu("RTX 4050"); d.setRam("16GB DDR5"); d.setStorage("512GB SSD"); p.setProductDetail(d);
        return p;
    }
    ProductRepository.AiProductRow row(Product p) {
        Map<String, Object> f = new HashMap<>(); f.put("id", p.getId()); f.put("name", p.getName()); f.put("price", p.getPrice()); f.put("stock", p.getStock());
        f.put("categoryName", "Gaming"); f.put("image", "assets/real.jpg"); f.put("cpu", p.getProductDetail().getCpu()); f.put("gpu", "RTX 4050");
        f.put("ram", "16GB DDR5"); f.put("storage", "512GB SSD");
        return new SpelAwareProxyProjectionFactory().createProjection(ProductRepository.AiProductRow.class, f);
    }
    void response(String selected) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", selected)))))));
        when(transport.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok(body));
    }
    void catalog() {
        List<Product> all = List.of(product(1), product(2), product(3), product(4), product(5), product(6));
        AiCatalogFixture.stub(products, all);
        when(products.findCurrentAiProducts(anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0); return all.stream().filter(p -> ids.contains(p.getId())).map(this::row).toList();
        });
    }
    AiResponseSettingsService.Snapshot selected(AiResponseSettings.Style style, AiResponseSettings.Length length, int max, boolean promo, boolean related) {
        return new AiResponseSettingsService.Snapshot(style, length, max, promo, related, 1L, null, 9L);
    }

    @Test void changesMaximumToneLengthPromotionAndRelatedProductsOnlyForSubsequentReplies() throws Exception {
        catalog();
        response("{\"message\":\"Không dùng số liệu do mô hình tự viết\",\"productIds\":[1,2,3,4,5,6]}");
        when(settings.current()).thenReturn(selected(AiResponseSettings.Style.FRIENDLY, AiResponseSettings.Length.SHORT, 1, false, false),
            selected(AiResponseSettings.Style.PROFESSIONAL, AiResponseSettings.Length.DETAILED, 5, true, true));
        AiReplyDTO first = consultant.consultWithProducts("Tìm laptop gaming dưới 20 triệu", List.of());
        AiReplyDTO next = consultant.consultWithProducts("Tìm laptop gaming dưới 20 triệu", List.of());
        assertEquals(1, first.products().size()); assertTrue(first.reply().startsWith("Mình đã tìm"));
        assertEquals(0, first.products().getFirst().promotionPercent());
        assertEquals(5, next.products().size()); assertTrue(next.reply().contains("LAPZONE đã đối chiếu"));
        assertTrue(next.reply().contains("trang chi tiết"));
        assertEquals(10, next.products().getFirst().promotionPercent());
        assertEquals(PromotionService.getDiscountedPrice(product(1)), next.products().getFirst().price());
        assertFalse(next.reply().contains("Không dùng số liệu do mô hình"));
        verify(transport, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test void relatedOptionFillsRemainingSlotsWithValidatedSameCategoryProductsButNeverOverridesAnInvalidAiChoice() throws Exception {
        catalog();
        when(settings.current()).thenReturn(selected(AiResponseSettings.Style.CONCISE, AiResponseSettings.Length.MEDIUM, 4, false, true));
        response("{\"message\":\"ignored\",\"productIds\":[1]}");
        AiReplyDTO reply = consultant.consultWithProducts("Tìm laptop gaming dưới 20 triệu", List.of());
        assertEquals(4, reply.products().size());
        assertEquals(1L, reply.products().getFirst().id());
        assertEquals(4, reply.products().stream().map(AiProductDTO::id).distinct().count());
        assertTrue(reply.products().stream().allMatch(p -> p.id() >= 1 && p.id() <= 6));
        assertTrue(reply.reply().contains("sản phẩm cùng danh mục"));
        response("{\"message\":\"ignored\",\"productIds\":[999]}");
        assertTrue(consultant.consultWithProducts("Tìm laptop gaming dưới 20 triệu", List.of()).products().isEmpty());
        verify(settings, times(2)).current();
    }

    @Test void allSettingsStillEnforceRealBudgetAndStockAndDoNotPermitUnverifiedIds() throws Exception {
        Product expensive = product(1); expensive.setPrice(50_000_000d);
        AiCatalogFixture.stub(products, List.of(expensive));
        when(settings.current()).thenReturn(selected(AiResponseSettings.Style.PROFESSIONAL, AiResponseSettings.Length.DETAILED, 5, true, true));
        assertTrue(consultant.consultWithProducts("Laptop dưới 20 triệu", List.of()).products().isEmpty());
        verifyNoInteractions(transport);
    }
}
