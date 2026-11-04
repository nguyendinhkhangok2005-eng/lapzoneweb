package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.entity.Category;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.ProductDetail;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConsultantServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private RestTemplate restTemplate;

    private AiConsultantService aiConsultantService;

    @BeforeEach
    void setUp() {
       aiConsultantService = new AiConsultantService(productRepository, "test-key", restTemplate, new ObjectMapper());
    }

    @Test
    void shouldIncludeAvailableProductFactsAndConversationHistoryInGeminiRequest() {
        Product product = product(1L, "Laptop Gaming Acer Nitro", 21_990_000d, 3, "NVIDIA RTX 3050");
        when(productRepository.findAvailableProductsForAi()).thenReturn(List.of(product));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("""
                {"candidates":[{"content":{"parts":[{"text":"{\\\"message\\\":\\\"Acer Nitro phù hợp.\\\",\\\"productIds\\\":[1]}"}]}}]}
                """));

        String reply = aiConsultantService.consult("Máy đó còn hàng không?",
            List.of(new ChatMessageDTO("user", "Tư vấn laptop gaming"), new ChatMessageDTO("model", "Acer Nitro")));

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), requestCaptor.capture(), eq(String.class));
        Map<?, ?> payload = (Map<?, ?>) requestCaptor.getValue().getBody();
        Map<?, ?> instruction = (Map<?, ?>) payload.get("systemInstruction");
        Map<?, ?> instructionPart = (Map<?, ?>) ((List<?>) instruction.get("parts")).get(0);
        String systemText = (String) instructionPart.get("text");
        List<?> contents = (List<?>) payload.get("contents");

        assertTrue(reply.startsWith("Acer Nitro phù hợp."));
        assertTrue(reply.contains("**Laptop Gaming Acer Nitro**"));
        assertTrue(reply.contains("21.990.000 VNĐ"));
        assertTrue(reply.contains("/product_detail?id=1"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent",
            urlCaptor.getValue());
        assertEquals("test-key", requestCaptor.getValue().getHeaders().getFirst("x-goog-api-key"));
        assertEquals("application/json",
            requestCaptor.getValue().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(systemText.contains("[DỮ LIỆU SẢN PHẨM HIỆN CÓ]"));
        assertTrue(systemText.contains("ID: 1"));
        assertTrue(systemText.contains("21.990.000 VNĐ"));
        assertTrue(systemText.contains("RAM: 16GB"));
        assertTrue(systemText.contains("CPU: Intel Core i5"));
        assertTrue(systemText.contains("NVIDIA RTX 3050"));
        assertTrue(systemText.contains("Tồn kho: 3"));
        assertFalse(systemText.contains("SSD:"));
        assertFalse(systemText.contains("Chi tiết:"));
        assertEquals("user", ((Map<?, ?>) contents.get(0)).get("role"));
        assertEquals("model", ((Map<?, ?>) contents.get(1)).get("role"));
        List<?> currentTurnParts = (List<?>) ((Map<?, ?>) contents.get(2)).get("parts");
        assertEquals("Máy đó còn hàng không?", ((Map<?, ?>) currentTurnParts.get(0)).get("text"));
    }

    @Test
    void shouldLimitSmallCatalogToEightProducts() {
        List<Product> products = new ArrayList<>();
        for (long id = 1; id <= 49; id++) {
            products.add(product(id, "Laptop " + id, 10_000_000d, 1, "Integrated graphics"));
        }
        when(productRepository.findAvailableProductsForAi()).thenReturn(products);

        String context = aiConsultantService.buildProductContext();

        assertTrue(context.contains("Laptop 1"));
        assertTrue(context.contains("Laptop 8"));
        assertFalse(context.contains("Laptop 9"));
        assertEquals(8, context.lines().count());
    }

    @Test
    void shouldLimitLargeCatalogToRelevantProductsForTheQuestion() {
        List<Product> matchingProducts = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            matchingProducts.add(product(id, "Gaming Acer RTX " + id, 19_000_000d, 2, "RTX 4050"));
        }
        matchingProducts.add(product(11L, "Gaming Acer RTX 11", 25_000_000d, 2, "RTX 4050"));
        when(productRepository.countAvailableProductsForAi()).thenReturn(50L);
        when(productRepository.findTopAvailableProductsForAi(eq("gaming"), any(Pageable.class)))
            .thenReturn(matchingProducts);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("""
                {"candidates":[{"content":{"parts":[{"text":"{\\\"message\\\":\\\"Gợi ý đã sẵn sàng.\\\",\\\"productIds\\\":[]}"}]}}]}
                """));

        aiConsultantService.consult("Tìm gaming RTX dưới 20 triệu", List.of());

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), requestCaptor.capture(), eq(String.class));
        Map<?, ?> payload = (Map<?, ?>) requestCaptor.getValue().getBody();
        Map<?, ?> instruction = (Map<?, ?>) payload.get("systemInstruction");
        String systemText = ((Map<?, ?>) ((List<?>) instruction.get("parts")).get(0)).get("text").toString();

        assertTrue(systemText.contains("Gaming Acer RTX"));
        assertFalse(systemText.contains("Laptop văn phòng 50"));
        assertFalse(systemText.contains("Gaming Acer RTX 11"));
        assertEquals(8, systemText.lines().filter(line -> line.startsWith("- ID:")).count());
    }

    @Test
    void shouldSwitchToFallbackImmediatelyAfterReadTimeout() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new ResourceAccessException("I/O error: Read timed out",
                new java.net.SocketTimeoutException("Read timed out")))
            .thenReturn(ResponseEntity.ok("""
                {"candidates":[{"content":{"parts":[{"text":"{\\\"message\\\":\\\"Đã thử lại thành công.\\\",\\\"productIds\\\":[]}"}]}}]}
                """));

        String reply = aiConsultantService.consult("Tư vấn laptop", List.of());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(2))
            .exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        assertEquals("Đã thử lại thành công.", reply);
        assertTrue(urlCaptor.getAllValues().get(0).contains("/models/gemini-3.1-flash-lite:"));
        assertTrue(urlCaptor.getAllValues().get(1).contains("/models/gemini-flash-lite-latest:"));
    }

    @Test
    void shouldStopRetriesAndFallbackWhenTotalDeadlineExpires() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenAnswer(invocation -> {
                Thread.sleep(300);
                throw serviceUnavailable();
            });
        AiConsultantService service = new AiConsultantService(productRepository, "test-key", restTemplate,
            new ObjectMapper(), Duration.ofMillis(250));

        String reply = service.consult("Tư vấn laptop", List.of());

        assertTrue(reply.contains("tạm thời chưa khả dụng"));
        verify(restTemplate, times(1))
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void shouldRetryServiceUnavailableAndSucceed() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(serviceUnavailable())
            .thenReturn(ResponseEntity.ok("""
                {"candidates":[{"content":{"parts":[{"text":"{\\\"message\\\":\\\"Gemini đã hồi phục.\\\",\\\"productIds\\\":[]}"}]}}]}
                """));

        String reply = aiConsultantService.consult("Tư vấn laptop", List.of());

        assertEquals("Gemini đã hồi phục.", reply);
        verify(restTemplate, times(2))
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void shouldRetryTooManyRequestsOnce() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, """
                    {"error":{"message":"Rate limit."}}
                    """.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
            .thenReturn(ResponseEntity.ok("""
                {"candidates":[{"content":{"parts":[{"text":"{\\\"message\\\":\\\"Đã thử lại sau giới hạn.\\\",\\\"productIds\\\":[]}"}]}}]}
                """));

        String reply = aiConsultantService.consult("Tư vấn laptop", List.of());

        assertEquals("Đã thử lại sau giới hạn.", reply);
        verify(restTemplate, times(2))
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void shouldUseFallbackModelAfterOneServiceUnavailableRetry() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(serviceUnavailable())
            .thenThrow(serviceUnavailable())
            .thenReturn(ResponseEntity.ok("""
                {"candidates":[{"content":{"parts":[{"text":"{\\\"message\\\":\\\"Phản hồi từ model dự phòng.\\\",\\\"productIds\\\":[]}"}]}}]}
                """));

        String reply = aiConsultantService.consult("Tư vấn laptop", List.of());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(3))
            .exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        assertEquals("Phản hồi từ model dự phòng.", reply);
        assertTrue(urlCaptor.getAllValues().subList(0, 2).stream()
            .allMatch(url -> url.contains("/models/gemini-3.1-flash-lite:")));
        assertTrue(urlCaptor.getValue().contains("/models/gemini-flash-lite-latest:"));
    }

    @Test
    void shouldNotRetryBadRequest() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                HttpHeaders.EMPTY,
                """
                {"error":{"message":"Invalid request."}}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8));

        String reply = aiConsultantService.consult("Tư vấn laptop", List.of());

        assertTrue(reply.contains("tạm thời chưa khả dụng"));
        verify(restTemplate, times(1))
            .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void shouldNotCallGeminiWithoutAnApiKey() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));
        AiConsultantService serviceWithoutKey =
            new AiConsultantService(productRepository, " ", restTemplate, new ObjectMapper());

        String reply = serviceWithoutKey.consult("Tư vấn laptop", List.of());

        assertTrue(reply.contains("tạm thời chưa khả dụng"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldNotSuggestProductsOutsideAnExplicitBudget() {
        when(productRepository.findAvailableProductsForAi()).thenReturn(
            List.of(product(1L, "Laptop Lapzone", 15_000_000d, 1, "Integrated graphics")));

        String reply = aiConsultantService.consult("Laptop dưới 10 triệu", List.of());

        assertTrue(reply.contains("chưa có sản phẩm còn hàng phù hợp"));
        verifyNoInteractions(restTemplate);
    }

    private Product product(Long id, String name, Double price, Integer stock, String gpu) {
        Category category = new Category();
        category.setName("Gaming");
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategory(category);
        ProductDetail detail = new ProductDetail();
        detail.setCpu("Intel Core i5");
        detail.setRam("16GB");
        detail.setStorage("512GB SSD");
        detail.setGpu(gpu);
        product.setProductDetail(detail);
        return product;
    }

    private HttpServerErrorException serviceUnavailable() {
        return HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
            HttpHeaders.EMPTY, """
                {"error":{"message":"High demand."}}
                """.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
