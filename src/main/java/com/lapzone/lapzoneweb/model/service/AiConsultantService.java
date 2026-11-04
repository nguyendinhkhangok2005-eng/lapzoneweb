package com.lapzone.lapzoneweb.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.ProductDetail;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiConsultantService {

    private static final Logger logger = LoggerFactory.getLogger(AiConsultantService.class);
    private static final int FULL_CATALOG_LIMIT = 50;
    private static final int MATCHED_PRODUCT_LIMIT = 8;
    private static final int MAX_GEMINI_ATTEMPTS = 2;
    private static final long[] GEMINI_RETRY_DELAYS_MILLIS = {1_000};
    private static final Duration DEFAULT_GEMINI_TOTAL_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration DEFAULT_GEMINI_READ_TIMEOUT = Duration.ofSeconds(15);
    private static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite";
    private static final String DEFAULT_GEMINI_FALLBACK_MODEL = "gemini-flash-lite-latest";
    private static final Duration GEMINI_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String SYSTEM_INSTRUCTION = """
        Bạn là Chuyên viên tư vấn bán hàng ảo chính thức của hệ thống cửa hàng laptop Lapzone.
        CHỈ ĐƯỢC tư vấn, báo giá và giới thiệu sản phẩm có trong [DỮ LIỆU SẢN PHẨM HIỆN CÓ].
        Không được tự bịa tên máy, cấu hình, giá bán, số lượng tồn kho hoặc thông tin ngoài dữ liệu.
        Nếu khách hỏi mẫu/mức giá không có, hãy lịch sự báo hiện chưa có và gợi ý 1-2 mẫu gần nhất trong danh sách.
        Nếu danh sách trống, không được gợi ý một sản phẩm cụ thể.
        Nếu câu hỏi không liên quan laptop, công nghệ hoặc mua hàng tại Lapzone, hãy từ chối khéo và đưa cuộc trò chuyện về chủ đề tư vấn laptop.
        Trả lời ngắn gọn, súc tích, thân thiện bằng tiếng Việt. Khi giới thiệu sản phẩm, nêu tên, giá VNĐ, điểm mạnh cấu hình và liên kết chi tiết đúng dạng /product_detail?id={id}.
        """;
    private static final Pattern PRICE_IN_MILLIONS = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(?:triệu|tr|m)\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> NON_SEARCH_TERMS = List.of(
        "toi", "minh", "ban", "cho", "mot", "may", "nao", "co", "khong", "gia",
        "duoi", "tren", "tam", "khoang", "can", "tim", "mua", "tu", "van", "giup",
        "lap", "laptop", "hang", "nhe", "tot", "re", "de", "hon", "voi", "va",
        "hay", "la", "the", "do", "nhu", "cau", "so", "sanh", "con", "hang");
    private static final List<String> CATALOG_SEARCH_TERMS = List.of(
        "gaming", "game", "office", "van", "phong", "do", "hoa",
        "asus", "acer", "lenovo", "dell", "hp", "msi", "gigabyte", "apple", "macbook",
        "intel", "amd", "ryzen", "core", "i3", "i5", "i7", "i9",
        "rtx", "gtx", "radeon", "arc", "gb");

    private final ProductRepository productRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String geminiApiKey;
    private final String geminiModel;
    private final String geminiFallbackModel;
    private final Duration totalTimeout;
    private final Duration readTimeout;
    private final long[] retryDelaysMillis;
    private final boolean usePerAttemptTimeouts;

    @Autowired
    public AiConsultantService(ProductRepository productRepository,
                               @Value("${gemini.api.key:}") String geminiApiKey,
                               @Value("${app.ai.model:gemini-3.1-flash-lite}") String geminiModel,
                               @Value("${app.ai.fallback-model:gemini-flash-lite-latest}") String geminiFallbackModel,
                               @Value("${app.ai.read-timeout-seconds:15}") long readTimeoutSeconds,
                               @Value("${app.ai.total-deadline-seconds:25}") long totalTimeoutSeconds) {
        this(productRepository, geminiApiKey, geminiModel, geminiFallbackModel,
            createRestTemplate(Duration.ofSeconds(readTimeoutSeconds)), new ObjectMapper(),
            Duration.ofSeconds(totalTimeoutSeconds), Duration.ofSeconds(readTimeoutSeconds),
            GEMINI_RETRY_DELAYS_MILLIS, true);
    }

    public AiConsultantService(ProductRepository productRepository, String geminiApiKey,
                               RestTemplate restTemplate, ObjectMapper objectMapper) {
        this(productRepository, geminiApiKey, DEFAULT_GEMINI_MODEL, DEFAULT_GEMINI_FALLBACK_MODEL,
            restTemplate, objectMapper, DEFAULT_GEMINI_TOTAL_TIMEOUT, DEFAULT_GEMINI_READ_TIMEOUT,
            GEMINI_RETRY_DELAYS_MILLIS, false);
    }

    public AiConsultantService(ProductRepository productRepository, String geminiApiKey, String geminiModel,
                               String geminiFallbackModel, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this(productRepository, geminiApiKey, geminiModel, geminiFallbackModel,
            restTemplate, objectMapper, DEFAULT_GEMINI_TOTAL_TIMEOUT, DEFAULT_GEMINI_READ_TIMEOUT,
            GEMINI_RETRY_DELAYS_MILLIS, false);
    }

    public AiConsultantService(ProductRepository productRepository, String geminiApiKey, RestTemplate restTemplate,
                               ObjectMapper objectMapper, Duration totalTimeout) {
        this(productRepository, geminiApiKey, DEFAULT_GEMINI_MODEL, DEFAULT_GEMINI_FALLBACK_MODEL,
            restTemplate, objectMapper, totalTimeout, DEFAULT_GEMINI_READ_TIMEOUT,
            GEMINI_RETRY_DELAYS_MILLIS, false);
    }

    private AiConsultantService(ProductRepository productRepository, String geminiApiKey, String geminiModel,
                                String geminiFallbackModel, RestTemplate restTemplate, ObjectMapper objectMapper,
                                Duration totalTimeout, Duration readTimeout, long[] retryDelaysMillis,
                                boolean usePerAttemptTimeouts) {
        this.productRepository = productRepository;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.geminiFallbackModel = geminiFallbackModel;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.totalTimeout = totalTimeout;
        this.readTimeout = readTimeout;
        this.retryDelaysMillis = retryDelaysMillis.clone();
        this.usePerAttemptTimeouts = usePerAttemptTimeouts;
    }

    public String consult(String userQuestion, List<ChatMessageDTO> history) {
        long deadlineNanos = System.nanoTime() + totalTimeout.toNanos();
        List<Product> contextProducts = selectContextProducts(userQuestion, retrieveContextProducts(userQuestion));
        String context = buildProductContext(contextProducts);

        if (contextProducts.isEmpty()) {
            return "Hiện tại Lapzone chưa có laptop còn hàng để gợi ý. Bạn vui lòng quay lại sau hoặc kiểm tra danh sách sản phẩm.";
        }
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            logger.error("Gemini API key is missing. Set GEMINI_API_KEY before starting Spring Boot.");
            return unavailableReply();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of(
            "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION + "\n\n[DỮ LIỆU SẢN PHẨM HIỆN CÓ]\n" + context))));
        payload.put("contents", buildContents(history, userQuestion));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        GeminiResult primaryResult = requestWithRetries(geminiModel, request, true, deadlineNanos);
        if (primaryResult.reply() != null) {
            return primaryResult.reply();
        }

        if (primaryResult.shouldUseFallback()) {
            GeminiResult fallbackResult = requestWithRetries(geminiFallbackModel, request, false, deadlineNanos);
            if (fallbackResult.reply() != null) {
                return fallbackResult.reply();
            }
        }
        return unavailableReply();
    }

    private GeminiResult requestWithRetries(String model, HttpEntity<Map<String, Object>> request,
                                             boolean retryTransientFailures, long deadlineNanos) {
        int maxAttempts = retryTransientFailures ? MAX_GEMINI_ATTEMPTS : 1;
        boolean lastFailureWasRetryable = false;
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long remainingNanos = remainingNanos(deadlineNanos);
            if (remainingNanos <= 0) {
                return new GeminiResult(null, false);
            }
            long requestStartedAt = System.nanoTime();
            try {
                ResponseEntity<String> response = exchange(url, request, remainingNanos);
                if (remainingNanos(deadlineNanos) <= 0) {
                    return new GeminiResult(null, false);
                }
                String body = response.getBody();
                if (body == null || body.isBlank()) {
                    logger.warn("Gemini returned an empty response for model {} (attempt {}/{}).",
                        model, attempt, maxAttempts);
                    return new GeminiResult(null, false);
                }

                JsonNode root = objectMapper.readTree(body);
                JsonNode textNode = root == null ? null
                    : root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (textNode != null && !textNode.isMissingNode() && !textNode.asText().isBlank()) {
                    if (remainingNanos(deadlineNanos) <= 0) {
                        return new GeminiResult(null, false);
                    }
                    logger.info("Gemini request completed for model {} in {} ms.",
                        model, elapsedMillisSince(requestStartedAt));
                    return new GeminiResult(textNode.asText().trim(), false);
                }
                logger.warn("Gemini returned no response text for model {} (attempt {}/{}).",
                    model, attempt, maxAttempts);
                return new GeminiResult(null, false);
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                lastFailureWasRetryable = status == 503 || status == 429;
                logger.error("Gemini request failed: HTTP {} for model {} (attempt {}/{}), after {} ms: {}",
                    status, model, attempt, maxAttempts, elapsedMillisSince(requestStartedAt),
                    providerErrorMessage(ex.getResponseBodyAsString()));
                if (!lastFailureWasRetryable || attempt == maxAttempts) {
                    return new GeminiResult(null, lastFailureWasRetryable && remainingNanos(deadlineNanos) > 0);
                }
            } catch (ResourceAccessException ex) {
                boolean timedOut = isTimeout(ex);
                logger.error("Gemini request failed: HTTP N/A for model {} (attempt {}/{}), after {} ms: {}",
                    model, attempt, maxAttempts, elapsedMillisSince(requestStartedAt),
                    networkFailureType(ex) + ": " + safeDiagnostic(ex.getMessage()));
                return new GeminiResult(null, timedOut && remainingNanos(deadlineNanos) > 0);
            } catch (RestClientException ex) {
                logger.error("Gemini request failed: HTTP N/A for model {} (attempt {}/{}), after {} ms: {}",
                    model, attempt, maxAttempts, elapsedMillisSince(requestStartedAt),
                    safeDiagnostic(ex.getMessage()));
                return new GeminiResult(null, false);
            } catch (JsonProcessingException ex) {
                logger.error("Could not parse Gemini response for model {} (attempt {}/{}), after {} ms: {}",
                    model, attempt, maxAttempts, elapsedMillisSince(requestStartedAt),
                    safeDiagnostic(ex.getOriginalMessage()));
                return new GeminiResult(null, false);
            }

            long remainingBeforeRetry = remainingNanos(deadlineNanos);
            if (remainingBeforeRetry <= 0) {
                return new GeminiResult(null, false);
            }
            try {
                long delayNanos = TimeUnit.MILLISECONDS.toNanos(retryDelaysMillis[attempt - 1]);
                TimeUnit.NANOSECONDS.sleep(Math.min(delayNanos, remainingBeforeRetry));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.warn("Gemini retry interrupted for model {} after attempt {}/{}.",
                    model, attempt, maxAttempts);
                return new GeminiResult(null, false);
            }
        }
        return new GeminiResult(null, lastFailureWasRetryable);
    }

    private ResponseEntity<String> exchange(String url, HttpEntity<Map<String, Object>> request,
                                             long remainingNanos) {
        if (!usePerAttemptTimeouts) {
            return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        }
        long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        long connectTimeoutMillis = Math.max(1,
            Math.min(GEMINI_CONNECT_TIMEOUT.toMillis(), remainingMillis / 2));
        long readTimeoutMillis = Math.max(1, Math.min(readTimeout.toMillis(), remainingMillis - connectTimeoutMillis));
        RestTemplate timedRestTemplate = createRestTemplate(
            Duration.ofMillis(connectTimeoutMillis), Duration.ofMillis(readTimeoutMillis));
        return timedRestTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }

    private long remainingNanos(long deadlineNanos) {
        return deadlineNanos - System.nanoTime();
    }

    private record GeminiResult(String reply, boolean shouldUseFallback) {
    }

    public String buildProductContext() {
        return buildProductContext(retrieveContextProducts(""));
    }

    private List<Map<String, Object>> buildContents(List<ChatMessageDTO> history, String userQuestion) {
        List<Map<String, Object>> contents = new ArrayList<>();
        if (history != null) {
            history.stream()
                .filter(Objects::nonNull)
                .filter(message -> "user".equals(message.role()) || "model".equals(message.role()))
                .forEach(message -> contents.add(content(message.role(), message.text())));
        }
        contents.add(content("user", userQuestion));
        return contents;
    }

    private Map<String, Object> content(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    private List<Product> retrieveContextProducts(String question) {
        long productCount = productRepository.countAvailableProductsForAi();
        if (productCount < FULL_CATALOG_LIMIT) {
            return productRepository.findAvailableProductsForAi();
        }
        String catalogKeyword = searchableTerms(question).stream()
            .filter(CATALOG_SEARCH_TERMS::contains)
            .findFirst()
            .orElse(null);
        List<Product> candidates = productRepository.findTopAvailableProductsForAi(
            catalogKeyword, PageRequest.of(0, FULL_CATALOG_LIMIT));
        if (candidates.isEmpty() && catalogKeyword != null) {
            candidates = productRepository.findTopAvailableProductsForAi(
                null, PageRequest.of(0, FULL_CATALOG_LIMIT));
        }
        return selectContextProducts(question, candidates);
    }

    private List<Product> selectContextProducts(String question, List<Product> products) {
        double budget = extractBudget(question);
        List<String> terms = searchableTerms(question);
        return products.stream()
            .sorted(Comparator.<Product>comparingInt(product -> relevanceScore(product, terms, budget))
                .reversed()
                .thenComparing(Product::getPrice, Comparator.nullsLast(Double::compareTo)))
            .limit(MATCHED_PRODUCT_LIMIT)
            .toList();
    }

    private int relevanceScore(Product product, List<String> terms, double budget) {
        String details = product.getProductDetail() == null ? "" : productDetailText(product.getProductDetail());
        String searchableText = normalize(safe(product.getName()) + " "
            + (product.getCategory() == null ? "" : safe(product.getCategory().getName())) + " " + details);
        int score = 0;
        for (String term : terms) {
            if (searchableText.contains(term)) {
                score += 2;
            }
        }
        if (budget > 0 && product.getPrice() != null) {
            score += product.getPrice() <= budget ? 4 : -1;
        }
        return score;
    }

    private List<String> searchableTerms(String question) {
        return java.util.Arrays.stream(normalize(question).split("[^\\p{L}\\p{N}]+"))
            .filter(term -> term.length() > 1 && !NON_SEARCH_TERMS.contains(term))
            .distinct()
            .toList();
    }

    private String normalize(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT)
            .replace("đ", "d")
            .replaceAll("[áàảãạăắằẳẵặâấầẩẫậ]", "a")
            .replaceAll("[éèẻẽẹêếềểễệ]", "e")
            .replaceAll("[íìỉĩị]", "i")
            .replaceAll("[óòỏõọôốồổỗộơớờởỡợ]", "o")
            .replaceAll("[úùủũụưứừửữự]", "u")
            .replaceAll("[ýỳỷỹỵ]", "y");
    }

    private double extractBudget(String question) {
        Matcher matcher = PRICE_IN_MILLIONS.matcher(Objects.toString(question, ""));
        double budget = 0;
        while (matcher.find()) {
            budget = Double.parseDouble(matcher.group(1).replace(',', '.')) * 1_000_000;
        }
        return budget;
    }

    private String buildProductContext(List<Product> products) {
        if (products.isEmpty()) {
            return "Hiện không có sản phẩm laptop còn hàng.";
        }
        return products.stream().limit(MATCHED_PRODUCT_LIMIT)
            .map(this::formatProduct).collect(Collectors.joining("\n"));
    }

    private String formatProduct(Product product) {
        ProductDetail detail = product.getProductDetail();
        String stock = product.getStock() == null ? "Không rõ" : product.getStock().toString();
        return "- ID: " + product.getId() + " | Tên: " + safe(product.getName())
            + " | Giá: " + formatCurrency(product.getPrice())
            + " | RAM: " + detailValue(detail, ProductDetail::getRam)
            + " | CPU: " + detailValue(detail, ProductDetail::getCpu)
            + " | GPU: " + detailValue(detail, ProductDetail::getGpu)
            + " | Tồn kho: " + stock;
    }

    private String productDetailText(ProductDetail detail) {
        return String.join(" ", safe(detail.getCpu()), safe(detail.getRam()), safe(detail.getStorage()),
            safe(detail.getGpu()), safe(detail.getScreen()), safe(detail.getMoreInfo()));
    }

    private String detailValue(ProductDetail detail, java.util.function.Function<ProductDetail, String> getter) {
        String value = detail == null ? null : getter.apply(detail);
        return value == null || value.isBlank() ? "Chưa cập nhật" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatCurrency(Double price) {
        return price == null ? "Chưa cập nhật" : String.format(Locale.forLanguageTag("vi-VN"), "%,.0f VNĐ", price);
    }

    private String unavailableReply() {
        return "Xin lỗi, chức năng tư vấn AI hiện tạm thời chưa khả dụng. Vui lòng thử lại sau hoặc xem trực tiếp danh sách sản phẩm của Lapzone.";
    }

    private String providerErrorMessage(String responseBody) {
        try {
            JsonNode errorMessage = objectMapper.readTree(responseBody).path("error").path("message");
            if (!errorMessage.isMissingNode() && !errorMessage.asText().isBlank()) {
                return safeDiagnostic(errorMessage.asText());
            }
        } catch (JsonProcessingException ex) {
            logger.warn("Gemini returned a non-JSON error response (HTTP details are available above).");
        }
        return "Provider did not return an error message.";
    }

    private String safeDiagnostic(String message) {
        if (message == null || message.isBlank()) {
            return "No additional diagnostic details.";
        }
        String sanitized = geminiApiKey == null || geminiApiKey.isBlank()
            ? message
            : message.replace(geminiApiKey, "[REDACTED]");
        sanitized = sanitized.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private String networkFailureType(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = Objects.toString(cause.getMessage(), "").toLowerCase(Locale.ROOT);
            if (message.contains("connect timed out") || message.contains("connection timed out")) {
                return "Gemini connect timeout (connection was not established within the configured 10 seconds)";
            }
            if (cause instanceof SocketTimeoutException || message.contains("read timed out")) {
                return "Gemini read timeout (response wait exceeded the configured "
                    + readTimeout.toSeconds() + " seconds)";
            }
            if (cause instanceof ConnectException) {
                return message.contains("timed out")
                    ? "Gemini connect timeout (connection was not established within the configured 10 seconds)"
                    : "Gemini connection refused or failed";
            }
            if (cause instanceof UnknownHostException) {
                return "Gemini DNS resolution failure";
            }
            cause = cause.getCause();
        }
        return "Gemini connection or network failure";
    }

    private boolean isTimeout(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = Objects.toString(cause.getMessage(), "").toLowerCase(Locale.ROOT);
            if (cause instanceof SocketTimeoutException || message.contains("timed out")
                || message.contains("timeout")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private long elapsedMillisSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static RestTemplate createRestTemplate(Duration readTimeout) {
        return createRestTemplate(GEMINI_CONNECT_TIMEOUT, readTimeout);
    }

    private static RestTemplate createRestTemplate(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return new RestTemplate(requestFactory);
    }
}
