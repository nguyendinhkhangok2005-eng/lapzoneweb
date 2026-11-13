package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.*;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AiRecommendationValidator {
    public enum Status {
        PASS("PASS"), FAIL("FAIL"), NOT_VERIFIED("NOT VERIFIED");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    public record Check(String label, Status status, String expected, String actual, String note) {}
    public record ProductReport(AiProductDTO product, boolean exists, String safeImage, List<Check> checks) {}
    public record Result(String reply, List<ProductReport> products, List<Check> overallChecks) {}
    private final ProductRepository products;
    public AiRecommendationValidator(ProductRepository products) { this.products = products; }

    public Result validate(String question, AiReplyDTO response) {
        List<AiProductDTO> returned = response.products() == null ? List.of() : response.products();
        List<Long> ids = returned.stream().filter(Objects::nonNull).map(p -> p.id()).filter(Objects::nonNull).distinct().toList();
        Map<Long, List<ProductRepository.AiProductRow>> rows = ids.isEmpty() ? Map.of()
            : products.findCurrentAiProducts(ids).stream().collect(Collectors.groupingBy(r -> r.getId()));
        AiProductRequirements requirements = AiProductRequirements.from(question, List.of());
        List<ProductReport> reports = returned.stream().filter(Objects::nonNull)
            .map(p -> inspect(question, p, p.id() == null ? List.of() : rows.getOrDefault(p.id(), List.of()), requirements)).toList();
        List<Check> overall = new ArrayList<>();
        if (reports.isEmpty()) overall.add(new Check("Có sản phẩm để đối chiếu", Status.NOT_VERIFIED, "Sản phẩm được trả về", "Không có sản phẩm",
            "Không thể suy ra chất lượng hoặc tính đầy đủ của câu trả lời chỉ từ một danh sách trống."));
        overall.add(new Check("Mức phù hợp và chất lượng tư vấn chủ quan", Status.NOT_VERIFIED, "Bằng chứng hiệu năng và tiêu chí đánh giá cụ thể", "Chưa có bằng chứng đầy đủ",
            "Tên danh mục, số lượt gợi ý hoặc các dữ kiện đúng không chứng minh máy phù hợp nhất với nhu cầu."));
        return new Result(response.reply(), reports, List.copyOf(overall));
    }

    private ProductReport inspect(String question, AiProductDTO p, List<ProductRepository.AiProductRow> rows, AiProductRequirements requirements) {
        List<Check> checks = new ArrayList<>();
        boolean exists = p.id() != null && p.id() > 0 && !rows.isEmpty();
        checks.add(check("Mã sản phẩm tồn tại", exists, "Bản ghi trong danh mục", String.valueOf(p.id()), "Đối chiếu trực tiếp theo mã sản phẩm."));
        if (!exists) {
            for (String label : List.of("Tên sản phẩm", "Ảnh thuộc sản phẩm", "Giá bán", "Thông số hiển thị", "Yêu cầu cấu hình đã nhận diện", "Ngân sách", "Trạng thái tồn kho", "Khả năng mua hiện tại", "Liên kết chi tiết"))
                checks.add(new Check(label, Status.NOT_VERIFIED, "Bản ghi sản phẩm hợp lệ", "Không có bản ghi", "Không đủ dữ liệu để xác minh."));
            return new ProductReport(p, false, null, checks);
        }
        var row = rows.getFirst();
        Product snapshot = snapshot(row);
        Double price = row.getPrice() != null && Double.isFinite(row.getPrice()) && row.getPrice() > 0 ? PromotionService.getDiscountedPrice(snapshot) : null;
        checks.add(row.getName() == null ? unknown("Tên sản phẩm", "Danh mục chưa có tên để đối chiếu.")
            : check("Tên sản phẩm", Objects.equals(row.getName(), p.name()), row.getName(), p.name(), "Tên phải trùng bản ghi sản phẩm."));
        Set<String> images = new LinkedHashSet<>();
        for (var imageRow : rows) {
            String main = storedImage(imageRow.getImage()), gallery = storedImage(imageRow.getGalleryImage());
            if (main != null) images.add(main); if (gallery != null) images.add(gallery);
        }
        boolean imageMatch = p.imageUrl() != null && images.contains(returnedImage(p.imageUrl()));
        checks.add(p.imageUrl() == null ? unknown("Ảnh thuộc sản phẩm", "Không có ảnh được trả về; chưa thể đối chiếu ảnh.")
            : check("Ảnh thuộc sản phẩm", imageMatch, images.toString(), p.imageUrl(), "Chỉ kiểm tra quan hệ ảnh trong DB, không xác minh nội dung điểm ảnh hoặc tải ảnh."));
        checks.add(price == null ? unknown("Giá bán", "Giá trong danh mục chưa hợp lệ.") : check("Giá bán", samePrice(price, p.price()), money(price), money(p.price()), "Dùng cùng quy tắc khuyến mại đang áp dụng trên cửa hàng."));
        int eligibleDiscount = row.getStock() != null && row.getStock() > 0 && price != null
            ? PromotionService.getDiscountPercent(snapshot) : 0;
        checks.add(check("Nhãn ưu đãi", p.promotionPercent() == 0 ||
                (p.promotionPercent() == eligibleDiscount && row.getStock() != null && row.getStock() > 0 && price != null),
            "Ưu đãi hiện đủ điều kiện: " + eligibleDiscount + "%", "Đã hiển thị: " + p.promotionPercent() + "%",
            "Nhãn có thể được tắt trong cấu hình; nếu hiển thị, tỷ lệ phải do dịch vụ khuyến mại hiện hành xác nhận."));
        checks.add(specificationFacts(row, p));
        checks.add(!requirements.hasSpecificationConstraints() || requirements.hasUnparsedSpecificationRequest(question) || requirements.hasMissingSpecificationData(snapshot)
            ? unknown("Yêu cầu cấu hình đã nhận diện", "Chưa nhận diện đầy đủ cấu hình định lượng. Các mô tả như mạnh, đẹp hoặc pin lâu chưa được xác minh.")
            : check("Yêu cầu cấu hình đã nhận diện", requirements.matchesRequiredSpecifications(snapshot), question,
                "CPU: " + row.getCpu() + "; GPU: " + row.getGpu() + "; RAM: " + row.getRam() + "; ổ cứng: " + row.getStorage(),
                "Chỉ xác minh ràng buộc có thể trích xuất bằng bộ lọc hiện có; không kiểm chứng hiệu năng thực tế hay các yêu cầu chưa được nhận diện."));
        checks.add(!requirements.hasBudgetConstraint() || price == null ? unknown("Ngân sách", "Không nhận diện được giới hạn ngân sách hoặc chưa có giá hợp lệ.")
            : check("Ngân sách", requirements.matchesPrice(price), question, money(price), "So ngân sách với giá thực trong DB, không dùng giá do phản hồi tự khai báo."));
        boolean available = price != null && row.getStock() != null && row.getStock() > 0;
        checks.add(row.getStock() == null && p.stock() == null ? unknown("Trạng thái tồn kho", "Danh mục chưa cập nhật tồn kho.")
            : check("Trạng thái tồn kho", Objects.equals(row.getStock(), p.stock()) && p.available() == available,
                "Kho: " + row.getStock() + "; " + (available ? "Còn hàng" : "Không thể mua"),
                "Kho: " + p.stock() + "; " + (p.available() ? "Còn hàng" : "Không thể mua"), "Đối chiếu số lượng và trạng thái có thể mua."));
        checks.add(row.getStock() == null || price == null ? unknown("Khả năng mua hiện tại", "Chưa có giá hoặc tồn kho hợp lệ.")
            : check("Khả năng mua hiện tại", available, "Có giá hợp lệ và kho lớn hơn 0", String.valueOf(row.getStock()), "Sản phẩm hết hàng không được coi là gợi ý có thể mua ngay."));
        checks.add(check("Liên kết chi tiết", Objects.equals("/product_detail?id=" + p.id(), p.detailUrl()), "/product_detail?id=" + p.id(), p.detailUrl(), "Liên kết phải trỏ đúng mã sản phẩm."));
        return new ProductReport(p, true, imageMatch ? p.imageUrl() : null, List.copyOf(checks));
    }

    private Check specificationFacts(ProductRepository.AiProductRow row, AiProductDTO p) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("CPU", row.getCpu()); facts.put("GPU", row.getGpu()); facts.put("RAM", row.getRam()); facts.put("Ổ cứng", row.getStorage());
        facts.put("Màn hình", row.getScreen()); facts.put("Pin", row.getBattery()); facts.put("Âm thanh", row.getAudio());
        facts.put("Cổng kết nối", row.getPorts()); facts.put("Không dây", row.getWireless()); facts.put("Khối lượng", row.getWeight());
        facts.put("Màu", row.getColor()); facts.put("Tình trạng", row.getConditionStatus()); facts.put("Bảo hành", row.getWarranty());
        if (p.specifications() == null || p.specifications().isEmpty()) return unknown("Thông số hiển thị", "Không có thông số trong phản hồi để đối chiếu.");
        boolean unknown = false;
        for (var spec : p.specifications().entrySet()) {
            if (!facts.containsKey(spec.getKey()) || facts.get(spec.getKey()) == null) { unknown = true; continue; }
            if (!Objects.equals(facts.get(spec.getKey()), spec.getValue())) return check("Thông số hiển thị", false,
                spec.getKey() + ": " + facts.get(spec.getKey()), spec.getKey() + ": " + spec.getValue(), "Thông số trả về khác danh mục.");
        }
        return unknown ? unknown("Thông số hiển thị", "Có thông số không có dữ liệu đối chiếu hoặc không thuộc trường được kiểm tra.")
            : check("Thông số hiển thị", true, facts.toString(), p.specifications().toString(), "Các thông số đã hiển thị khớp DB; không suy ra hiệu năng hoặc xác minh thông số bị bỏ sót.");
    }
    private Product snapshot(ProductRepository.AiProductRow r) {
        Product p = new Product(); p.setId(r.getId()); p.setName(r.getName()); p.setPrice(r.getPrice()); p.setStock(r.getStock());
        ProductDetail d = new ProductDetail(); d.setCpu(r.getCpu()); d.setGpu(r.getGpu()); d.setRam(r.getRam()); d.setStorage(r.getStorage());
        d.setScreen(r.getScreen()); d.setBattery(r.getBattery()); d.setAudio(r.getAudio()); d.setPorts(r.getPorts());
        d.setWireless(r.getWireless()); d.setWeight(r.getWeight()); d.setColor(r.getColor()); d.setConditionStatus(r.getConditionStatus()); d.setWarranty(r.getWarranty());
        p.setProductDetail(d); return p;
    }
    private String returnedImage(String url) {
        if (!url.startsWith("/assets/") && !url.startsWith("/uploads/")) return null;
        try {
            URI uri = URI.create(url);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            return storedImage(URLDecoder.decode(url, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) { return null; }
    }
    private String storedImage(String stored) {
        if (stored == null || stored.isBlank()) return null;
        String value = stored.strip();
        if (value.contains(":") || value.contains("\\") || value.contains("..") || value.contains("%") || value.startsWith("//") || value.chars().anyMatch(Character::isISOControl)) return null;
        if (value.startsWith("/")) value = value.substring(1);
        if (!value.startsWith("assets/") && !value.startsWith("uploads/")) value = "assets/" + value;
        return "/" + Arrays.stream(value.split("/")).map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")).collect(Collectors.joining("/"));
    }
    private boolean samePrice(Double expected, Double actual) { return actual != null && Double.isFinite(actual) && BigDecimal.valueOf(expected).compareTo(BigDecimal.valueOf(actual)) == 0; }
    private String money(Double value) { return value == null || !Double.isFinite(value) ? "Chưa có giá hợp lệ" : String.format(Locale.forLanguageTag("vi-VN"), "%,.0f ₫", value); }
    private Check check(String label, boolean pass, String expected, String actual, String note) { return new Check(label, pass ? Status.PASS : Status.FAIL, expected, actual, note); }
    private Check unknown(String label, String note) { return new Check(label, Status.NOT_VERIFIED, "Chưa có đủ bằng chứng", "Chưa xác minh", note); }
}
