package com.lapzone.lapzoneweb.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.ProductDetail;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiConsultantService {

    private final ProductRepository productRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    public AiConsultantService(ProductRepository productRepository) {
        this.productRepository = productRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String consult(String userQuestion) {
        String context = buildProductContext();

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return fallbackConsultation(userQuestion, context);
        }

        String prompt = """
            Bạn là AI tư vấn laptop của Lapzone.
            Dựa trên danh sách sản phẩm sau:
            %s

            Khách hàng hỏi: "%s"

            Hướng dẫn trả lời:
            - Trả lời ngắn gọn, lịch sự, dễ hiểu.
            - Nên đề xuất tối đa 3 sản phẩm phù hợp với nhu cầu và ngân sách.
            - Nếu sản phẩm phù hợp, nêu lý do rõ ràng theo cấu hình.
            - Nếu không tìm thấy, hãy gợi ý các mức giá tương ứng.
            - Chỉ trả lời bằng tiếng Việt.
            - Không dài quá 6 câu.
            """.formatted(context, userQuestion);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey;

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");

            if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                return textNode.asText().trim();
            }
        } catch (Exception ex) {
            return fallbackConsultation(userQuestion, context);
        }

        return fallbackConsultation(userQuestion, context);
    }

    public String buildProductContext() {
        List<Product> products = productRepository.findAll();

        if (products.isEmpty()) {
            return "Hiện chưa có sản phẩm nào trong kho.";
        }

        return products.stream()
            .sorted(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Double::compareTo)))
            .limit(15)
            .map(this::formatProduct)
            .collect(Collectors.joining("\n"));
    }

    private String formatProduct(Product product) {
        ProductDetail detail = product.getProductDetail();
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "Chưa xác định";

        String cpu = detail != null && detail.getCpu() != null ? detail.getCpu() : "N/A";
        String ram = detail != null && detail.getRam() != null ? detail.getRam() : "N/A";
        String storage = detail != null && detail.getStorage() != null ? detail.getStorage() : "N/A";
        String gpu = detail != null && detail.getGpu() != null ? detail.getGpu() : "N/A";
        String screen = detail != null && detail.getScreen() != null ? detail.getScreen() : "N/A";
        String info = detail != null && detail.getMoreInfo() != null ? detail.getMoreInfo() : "";

        return "- " + product.getName() + " | Giá: " + formatCurrency(product.getPrice()) + " | Hãng: " + categoryName
            + " | CPU: " + cpu + " | RAM: " + ram + " | SSD: " + storage + " | GPU: " + gpu
            + " | Màn hình: " + screen + (info.isBlank() ? "" : " | Ghi chú: " + info);
    }

    private String formatCurrency(Double price) {
        if (price == null) {
            return "Liên hệ";
        }
        return String.format("%,.0fđ", price);
    }

    private String fallbackConsultation(String userQuestion, String context) {
        String lowerQuestion = userQuestion.toLowerCase();

        List<Product> products = productRepository.findAll();
        List<Product> filtered = new ArrayList<>();

        for (Product product : products) {
            String name = product.getName() == null ? "" : product.getName().toLowerCase();
            String category = product.getCategory() != null && product.getCategory().getName() != null ? product.getCategory().getName().toLowerCase() : "";
            String detailText = "";
            if (product.getProductDetail() != null) {
                ProductDetail detail = product.getProductDetail();
                detailText = (detail.getCpu() == null ? "" : detail.getCpu()) + " "
                    + (detail.getRam() == null ? "" : detail.getRam()) + " "
                    + (detail.getStorage() == null ? "" : detail.getStorage()) + " "
                    + (detail.getGpu() == null ? "" : detail.getGpu()) + " "
                    + (detail.getScreen() == null ? "" : detail.getScreen());
            }

            String combined = name + " " + category + " " + detailText;
            if (combined.toLowerCase().contains("gaming") && lowerQuestion.contains("game")) {
                filtered.add(product);
            } else if (combined.toLowerCase().contains("office") && lowerQuestion.contains("văn phòng")) {
                filtered.add(product);
            } else if ((combined.toLowerCase().contains("design") || combined.toLowerCase().contains("graphics") || combined.toLowerCase().contains("rtx")) && lowerQuestion.contains("đồ họa")) {
                filtered.add(product);
            }
        }

        if (!filtered.isEmpty()) {
            Product best = filtered.stream().min(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Double::compareTo))).orElse(filtered.get(0));
            return "Lapzone gợi ý bạn xem " + best.getName() + " với giá " + formatCurrency(best.getPrice()) + ". Đây là lựa chọn phù hợp cho nhu cầu của bạn, và cấu hình hiện đang cân đối để đủ sức đáp ứng công việc/làm việc hàng ngày.";
        }

        return "Lapzone có thể tư vấn cho bạn theo ngân sách và nhu cầu. Hãy mô tả rõ hơn như: mục đích dùng (gaming, học tập, đồ họa), ngân sách tối đa và mức độ vận hành mong muốn.";
    }
}
