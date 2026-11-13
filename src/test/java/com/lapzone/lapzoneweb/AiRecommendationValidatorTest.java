package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.service.AiRecommendationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import java.util.*;
import static com.lapzone.lapzoneweb.model.service.AiRecommendationValidator.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiRecommendationValidatorTest {
    final ProductRepository repository = mock(ProductRepository.class);
    final AiRecommendationValidator validator = new AiRecommendationValidator(repository);
    Map<String, Object> facts(long id, Double price, Integer stock) {
        Map<String, Object> f = new HashMap<>();
        f.put("id", id); f.put("name", "Laptop Gaming"); f.put("price", price); f.put("stock", stock);
        f.put("image", "assets/real.jpg"); f.put("galleryImage", "gallery two.jpg");
        f.put("cpu", "Intel Core i7"); f.put("gpu", "RTX 4060"); f.put("ram", "16GB DDR5"); f.put("storage", "1TB SSD");
        return f;
    }
    void source(Map<String, Object> facts) {
        when(repository.findCurrentAiProducts(anyList())).thenReturn(List.of(new SpelAwareProxyProjectionFactory()
            .createProjection(ProductRepository.AiProductRow.class, facts)));
    }
    AiProductDTO product(long id, double price, String image, Map<String, String> specs, int stock, boolean available) {
        return new AiProductDTO(id, "Laptop Gaming", price, image, specs, stock, available, "/product_detail?id=" + id);
    }
    AiRecommendationValidator.ProductReport validate(String question, AiProductDTO p) {
        return validator.validate(question, new AiReplyDTO("Phản hồi", List.of(p))).products().getFirst();
    }
    AiRecommendationValidator.Status status(AiRecommendationValidator.ProductReport report, String label) {
        return report.checks().stream().filter(c -> c.label().equals(label)).findFirst().orElseThrow().status();
    }

    @Test void verifiesFactsButNeverClaimsSubjectiveQualityIsVerified() {
        source(facts(1001, 18_000_000d, 3));
        AiProductDTO p = product(1001, 18_000_000, "/assets/real.jpg", Map.of("RAM", "16GB DDR5", "CPU", "Intel Core i7"), 3, true);
        var result = validator.validate("Gaming CPU i7 RAM 16GB SSD 512GB dưới 20 triệu", new AiReplyDTO("Phản hồi", List.of(p)));
        assertTrue(result.products().getFirst().checks().stream().allMatch(c -> c.status() == PASS));
        assertEquals(NOT_VERIFIED, result.overallChecks().getLast().status());
        verify(repository, never()).save(any());
    }
    @Test void failsUnknownAndNullProductIdsWithoutInventingVerification() {
        when(repository.findCurrentAiProducts(anyList())).thenReturn(List.of());
        var missing = validate("dưới 20 triệu", product(999, 1, "/assets/fake.jpg", Map.of(), 1, true));
        assertEquals(FAIL, status(missing, "Mã sản phẩm tồn tại"));
        assertEquals(NOT_VERIFIED, status(missing, "Giá bán"));
        AiProductDTO invalid = new AiProductDTO(null, "Fake", 1d, null, Map.of(), 1, true, "/product_detail?id=0");
        assertEquals(FAIL, status(validate("laptop", invalid), "Mã sản phẩm tồn tại"));
    }
    @Test void detectsFabricatedNameImagePriceSpecsStockAndLinkAndUsesDatabasePriceForBudget() {
        source(facts(1001, 26_000_000d, 3));
        AiProductDTO forged = new AiProductDTO(1001L, "Invented name", 1d, "/assets/another-product.jpg",
            Map.of("RAM", "64GB"), 99, true, "/product_detail?id=555");
        var report = validate("RAM 32GB dưới 20 triệu", forged);
        for (String label : List.of("Tên sản phẩm", "Ảnh thuộc sản phẩm", "Giá bán", "Thông số hiển thị", "Yêu cầu cấu hình đã nhận diện", "Ngân sách", "Trạng thái tồn kho", "Liên kết chi tiết"))
            assertEquals(FAIL, status(report, label), label);
        assertNull(report.safeImage());
    }
    @Test void acceptsOnlyGalleryImagesBelongingToTheSameProductAndRejectsExternalUrlsAndQueryTricks() {
        source(facts(1001, 18_000_000d, 3));
        assertEquals(PASS, status(validate("laptop", product(1001, 18_000_000, "/assets/gallery%20two.jpg", Map.of(), 3, true)), "Ảnh thuộc sản phẩm"));
        for (String image : List.of("https://fake/image.jpg", "/assets/real.jpg?wrong=1", "/assets/%2e%2e/fake.jpg", "/assets/%XX.jpg"))
            assertEquals(FAIL, status(validate("laptop", product(1001, 18_000_000, image, Map.of(), 3, true)), "Ảnh thuộc sản phẩm"));
    }
    @Test void missingDataAndUnparsedRequirementsStayNotVerified() {
        var f = facts(1001, 18_000_000d, null); f.put("ram", null); source(f);
        AiProductDTO p = new AiProductDTO(1001L, "Laptop Gaming", 18_000_000d, null, Map.of(), null, false, "/product_detail?id=1001");
        var report = validate("RAM 32GB", p);
        for (String label : List.of("Ảnh thuộc sản phẩm", "Thông số hiển thị", "Yêu cầu cấu hình đã nhận diện", "Ngân sách", "Trạng thái tồn kho", "Khả năng mua hiện tại"))
            assertEquals(NOT_VERIFIED, status(report, label), label);
        source(facts(1001, 18_000_000d, 3));
        assertEquals(NOT_VERIFIED, status(validate("RAM 16GB CPU thần kỳ", p), "Yêu cầu cấu hình đã nhận diện"));
    }
    @Test void outOfStockCanHaveAccurateStockButStillFailsPurchasability() {
        source(facts(1001, 18_000_000d, 0));
        var report = validate("laptop", product(1001, 18_000_000, "/assets/real.jpg", Map.of(), 0, false));
        assertEquals(PASS, status(report, "Trạng thái tồn kho")); assertEquals(FAIL, status(report, "Khả năng mua hiện tại"));
    }
    @Test void usesStorefrontPromotionsAndLeavesEmptyRecommendationsUnverified() {
        source(facts(1, 20_000_000d, 3));
        var report = validate("dưới 19 triệu", product(1, 18_000_000, "/assets/real.jpg", Map.of(), 3, true));
        assertEquals(PASS, status(report, "Giá bán")); assertEquals(PASS, status(report, "Ngân sách"));
        var inventedBadge = new AiProductDTO(1L, "Laptop Gaming", 18_000_000d, "/assets/real.jpg", Map.of(), 3, true,
            "/product_detail?id=1", 50);
        assertEquals(FAIL, status(validate("dưới 19 triệu", inventedBadge), "Nhãn ưu đãi"));
        var validBadge = new AiProductDTO(1L, "Laptop Gaming", 18_000_000d, "/assets/real.jpg", Map.of(), 3, true,
            "/product_detail?id=1", 10);
        assertEquals(PASS, status(validate("dưới 19 triệu", validBadge), "Nhãn ưu đãi"));
        var empty = validator.validate("laptop", new AiReplyDTO("Không có sản phẩm"));
        assertTrue(empty.overallChecks().stream().allMatch(c -> c.status() == NOT_VERIFIED));
    }
}
