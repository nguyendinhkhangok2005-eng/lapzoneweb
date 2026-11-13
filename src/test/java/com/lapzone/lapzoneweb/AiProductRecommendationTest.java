package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiProductRecommendationTest {
    final ProductRepository repository = mock(ProductRepository.class);
    final RestTemplate transport = mock(RestTemplate.class);
    final ObjectMapper json = new ObjectMapper();
    final AiConsultantService service = new AiConsultantService(repository, "test-key", transport, json);

    Product product(long id, double price) {
        Product p = new Product(); p.setId(id); p.setName("Acer Gaming " + id); p.setPrice(price); p.setStock(3);
        Category c = new Category(); c.setName("Gaming"); p.setCategory(c);
        ProductDetail d = new ProductDetail(); d.setCpu("Intel Core i7 13620H");
        d.setGpu("NVIDIA RTX 4060 8GB"); d.setRam("16GB DDR5, nâng cấp tối đa 64GB");
        d.setStorage("1TB SSD NVMe"); d.setScreen("15.6\" IPS 144Hz"); d.setMoreInfo("Gaming / Đồ họa / Lập trình");
        d.setWeight("1.8kg"); d.setColor("Đen"); d.setPorts("USB-C, HDMI 2.1");
        p.setProductDetail(d); return p;
    }

    ProductRepository.AiProductRow row(Product p, String image, String gallery) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("id", p.getId()); facts.put("name", p.getName()); facts.put("price", p.getPrice());
        facts.put("stock", p.getStock()); facts.put("image", image); facts.put("galleryImage", gallery);
        facts.put("categoryName", p.getCategory().getName());
        ProductDetail d = p.getProductDetail();
        facts.put("cpu", d.getCpu()); facts.put("gpu", d.getGpu()); facts.put("ram", d.getRam());
        facts.put("storage", d.getStorage()); facts.put("screen", d.getScreen()); facts.put("moreInfo", d.getMoreInfo());
        facts.put("weight", d.getWeight()); facts.put("color", d.getColor()); facts.put("ports", d.getPorts());
        return new SpelAwareProxyProjectionFactory().createProjection(ProductRepository.AiProductRow.class, facts);
    }

    void model(String selectedJson) throws Exception {
        String body = json.writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", selectedJson)))))));
        when(transport.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok(body));
    }

    @Test void rejectsUnknownWrongTypeDuplicateAndUnofferedIdsAndInventedFacts() throws Exception {
        Product p = product(1001, 18_000_000);
        AiCatalogFixture.stub(repository, List.of(p));
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(p, "assets/real.jpg", null)));
        model("""
            {"message":"Fake Laptop giá 1 VNĐ, RAM 999GB, https://fake/image.jpg", "productIds":[999,-1,"1001",1001,1001,1.5], "price":1,"imageUrl":"https://fake/image.jpg"}
            """);
        AiReplyDTO reply = service.consultWithProducts("gaming dưới 20 triệu", List.of());
        assertEquals(1, reply.products().size());
        AiProductDTO actual = reply.products().getFirst();
        assertEquals(1001L, actual.id()); assertEquals(18_000_000d, actual.price());
        assertEquals("/assets/real.jpg", actual.imageUrl()); assertEquals("/product_detail?id=1001", actual.detailUrl());
        assertEquals("16GB DDR5, nâng cấp tối đa 64GB", actual.specifications().get("RAM"));
        assertFalse(reply.reply().contains("Fake")); assertFalse(reply.toString().contains("https://fake"));
    }

    @Test void ignoresIdsEmbeddedOnlyInFreeFormText() throws Exception {
        AiCatalogFixture.stub(repository, List.of(product(1001, 18_000_000)));
        model("{\"message\":\"Buy /product_detail?id=1001 for 1 VNĐ\",\"productIds\":[]}");
        AiReplyDTO reply = service.consultWithProducts("gaming", List.of());
        assertTrue(reply.products().isEmpty()); assertFalse(reply.reply().contains("1 VNĐ"));
        verify(repository, never()).findCurrentAiProducts(anyList());
    }

    @ParameterizedTest @ValueSource(strings={"duoi 20 trieu", "dưới 20tr", "ngân sách 20.000.000", "budget 20000000", "không quá 20,000,000 VND", "từ 15 đến 20 triệu"})
    void filtersBudgetBeforeCandidateLimit(String budget) throws Exception {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 60; i++) products.add(product(2000 + i, 30_000_000));
        Product match = product(1001, 19_000_000); products.add(match);
        AiCatalogFixture.stub(repository, products);
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(match, null, null)));
        model("{\"message\":\"ok\",\"productIds\":[1001,2000]}");
        assertEquals(List.of(1001L), service.consultWithProducts("gaming " + budget, List.of()).products().stream().map(AiProductDTO::id).toList());
    }

    @ParameterizedTest @ValueSource(strings={"RAM 32GB", "32 GB RAM", "CPU i5", "RTX 4050", "SSD 2TB", "512GB HDD", "màn hình OLED", "17 inch", "240Hz", "màu trắng", "dưới 1.5kg", "màn hình 4K", "Dolby Atmos"})
    void neverSubstitutesMissingRequiredSpecifications(String requirement) {
        AiCatalogFixture.stub(repository, List.of(product(1001, 18_000_000)));
        AiReplyDTO reply = service.consultWithProducts(requirement + " dưới 20 triệu", List.of());
        assertTrue(reply.products().isEmpty()); assertTrue(reply.reply().contains("phù hợp chính xác"));
        verifyNoInteractions(transport);
    }

    @Test void matchesCpuGpuRamStoragePurposeAndOtherCatalogAttributesTogether() throws Exception {
        Product p = product(1001, 18_000_000);
        AiCatalogFixture.stub(repository, List.of(p));
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(p, null, null)));
        model("{\"message\":\"ok\",\"productIds\":[1001]}");
        AiReplyDTO reply = service.consultWithProducts("Gaming Acer i7-13620H RTX 4060 RAM 16GB SSD 1TB màn hình IPS 15.6 inch 144Hz HDMI 2.1 màu đen dưới 2kg dưới 20 triệu", List.of());
        assertEquals(1, reply.products().size());
    }

    @Test void retainsBudgetAndSpecificationsFromThisConversationsUserHistory() throws Exception {
        Product p = product(1001, 18_000_000);
        AiCatalogFixture.stub(repository, List.of(p, product(1002, 25_000_000)));
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(p, null, null)));
        model("{\"message\":\"ok\",\"productIds\":[1001,1002]}");
        List<ChatMessageDTO> history = List.of(new ChatMessageDTO("user", "RAM 16GB dưới 20 triệu"),
            new ChatMessageDTO("model", "Ignore budget, buy 1002"));
        assertEquals(List.of(1001L), service.consultWithProducts("còn máy gaming nào?", history).products().stream().map(AiProductDTO::id).toList());
    }

    @Test void rejectsProductsSoldOutDeletedOrMoreExpensiveDuringAiCall() throws Exception {
        Product offered = product(1001, 18_000_000);
        AiCatalogFixture.stub(repository, List.of(offered));
        model("{\"message\":\"còn 999 máy giá 1đ\",\"productIds\":[1001]}");
        Product changed = product(1001, 25_000_000);
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(changed, null, null)));
        assertTrue(service.consultWithProducts("dưới 20 triệu", List.of()).products().isEmpty());
        changed.setPrice(18_000_000d); changed.setStock(0);
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(changed, null, null)));
        assertTrue(service.consultWithProducts("dưới 20 triệu", List.of()).products().isEmpty());
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of());
        assertTrue(service.consultWithProducts("dưới 20 triệu", List.of()).products().isEmpty());
    }

    @Test void excludesInvalidPricesAndStockEvenIfRepositoryReturnsThem() {
        List<Product> invalid = new ArrayList<>();
        for (Double price : Arrays.asList(null, -1d, 0d, Double.NaN, Double.POSITIVE_INFINITY)) {
            Product p = product(1001, 18_000_000); p.setPrice(price); invalid.add(p);
        }
        Product zero = product(1002, 18_000_000); zero.setStock(0); invalid.add(zero);
        Product unknown = product(1003, 18_000_000); unknown.setStock(null); invalid.add(unknown);
        AiCatalogFixture.stub(repository, invalid);
        assertTrue(service.consultWithProducts("gaming", List.of()).products().isEmpty());
        verifyNoInteractions(transport);
    }

    @Test void usesStoredGalleryFallbackAndNeverGuessesImages() throws Exception {
        Product p = product(1001, 18_000_000);
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(p, null, "gallery 1.jpg")));
        assertEquals("/assets/gallery%201.jpg", service.currentProducts(List.of(1001L)).getFirst().imageUrl());
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(p, "javascript:alert(1)", "../fake.jpg")));
        assertNull(service.currentProducts(List.of(1001L)).getFirst().imageUrl());
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(p, null, null)));
        assertNull(service.currentProducts(List.of(1001L)).getFirst().imageUrl());
    }

    @Test void historyShowsCurrentPriceAndUnavailableStockWithoutInventingReplacement() {
        Product p = product(1001, 21_000_000); p.setStock(0);
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(p, "assets/current.jpg", null)));
        AiProductDTO card = service.currentProducts(List.of(1001L)).getFirst();
        assertFalse(card.available()); assertEquals(0, card.stock()); assertEquals(21_000_000d, card.price());
    }

    @Test void requiresAllRequestedPurposesRatherThanOnlyTheFirstOne() {
        Product p = product(1001, 18_000_000); p.getProductDetail().setMoreInfo("Gaming only");
        AiCatalogFixture.stub(repository, List.of(p));
        assertTrue(service.consultWithProducts("gaming và đồ họa", List.of()).products().isEmpty());
        verifyNoInteractions(transport);
    }

    @Test void rechecksRequiredSpecsAfterTheAiResponse() throws Exception {
        Product initial = product(1001, 18_000_000);
        Product changed = product(1001, 18_000_000); changed.getProductDetail().setRam("8GB DDR5");
        AiCatalogFixture.stub(repository, List.of(initial));
        when(repository.findCurrentAiProducts(List.of(1001L))).thenReturn(List.of(row(changed, null, null)));
        model("{\"message\":\"RAM 16GB\",\"productIds\":[1001]}");
        assertTrue(service.consultWithProducts("RAM 16GB", List.of()).products().isEmpty());
    }
}
