package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.entity.AiResponseSettings.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AiResponseSettingsService {
    private final AiResponseSettingsRepository settings;
    private final AiResponseSettingsChangeRepository changes;
    private final UserRepository users;
    public AiResponseSettingsService(AiResponseSettingsRepository settings, AiResponseSettingsChangeRepository changes, UserRepository users) {
        this.settings = settings; this.changes = changes; this.users = users;
    }
    public record Input(String style, String responseLength, String maxRecommendations,
                        boolean highlightPromotions, boolean suggestRelatedProducts, Long expectedVersion) {}
    public record Snapshot(Style style, Length responseLength, int maxRecommendations,
                           boolean highlightPromotions, boolean suggestRelatedProducts,
                           Long version, Instant updatedAt, Long updatedById) {
        public static Snapshot defaults() { return new Snapshot(Style.FRIENDLY, Length.MEDIUM, 3, false, false, null, null, null); }
    }
    public record ChangeView(String when, Long adminId, String action, String before, String after) {}

    public Snapshot current() {
        return settings.findById(1L).map(this::from).orElseGet(Snapshot::defaults);
    }
    public Input asInput(Snapshot snapshot) {
        return new Input(snapshot.style().name(), snapshot.responseLength().name(),
            Integer.toString(snapshot.maxRecommendations()), snapshot.highlightPromotions(),
            snapshot.suggestRelatedProducts(), snapshot.version());
    }
    public Snapshot validate(Input input) {
        if (input == null) throw bad("Cấu hình không hợp lệ.");
        Style style; Length length; int max;
        try { style = Style.valueOf(input.style()); length = Length.valueOf(input.responseLength());
            max = Integer.parseInt(input.maxRecommendations()); }
        catch (RuntimeException ex) { throw bad("Phong cách, độ dài hoặc số lượng gợi ý không hợp lệ."); }
        if (max < 1 || max > 5) throw bad("Chỉ được gợi ý từ 1 đến 5 sản phẩm.");
        return new Snapshot(style, length, max, input.highlightPromotions(), input.suggestRelatedProducts(),
            input.expectedVersion(), null, null);
    }
    public String preview(Input input) {
        Snapshot sample = validate(input);
        return "Bản xem trước cách diễn đạt, không phải tư vấn sản phẩm thật:\n"
            + productIntroduction(sample, 0)
            + "\nGiới hạn: " + sample.maxRecommendations() + " sản phẩm. Đánh dấu ưu đãi: "
            + (sample.highlightPromotions() ? "Có" : "Không") + ". Gợi ý liên quan: "
            + (sample.suggestRelatedProducts() ? "Có" : "Không")
            + ".\nThẻ sản phẩm thật chỉ xuất hiện sau khi dữ liệu danh mục được xác minh.";
    }
    @Transactional
    public void save(Input input) { apply(validate(input), AiResponseSettingsChange.Action.UPDATE); }
    @Transactional
    public void reset(Long expectedVersion) {
        Snapshot defaults = Snapshot.defaults();
        apply(new Snapshot(defaults.style(), defaults.responseLength(), defaults.maxRecommendations(), false, false,
            expectedVersion, null, null), AiResponseSettingsChange.Action.RESET);
    }
    public Page<ChangeView> history(int page) {
        adminId();
        if (page < 0 || page > 1000) throw bad("Số trang không hợp lệ.");
        return changes.findAllByOrderByChangedAtDescIdDesc(PageRequest.of(page, 10)).map(c -> new ChangeView(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(c.getChangedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh"))),
            c.getAdminId(), c.getAction().getLabel(), c.getBeforeValue(), c.getAfterValue()));
    }
    private void apply(Snapshot requested, AiResponseSettingsChange.Action action) {
        Long actor = adminId();
        AiResponseSettings entity = settings.lockCurrent().orElse(null);
        Snapshot before = entity == null ? Snapshot.defaults() : from(entity);
        if (!Objects.equals(before.version(), requested.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cấu hình đã được thay đổi. Vui lòng tải lại trang trước khi lưu.");
        if (entity == null) entity = new AiResponseSettings();
        entity.apply(requested.style(), requested.responseLength(), requested.maxRecommendations(),
            requested.highlightPromotions(), requested.suggestRelatedProducts(), actor);
        settings.save(entity);
        changes.save(new AiResponseSettingsChange(actor, action, summary(before), summary(requested)));
    }
    private Snapshot from(AiResponseSettings s) {
        int safeMax = Math.max(1, Math.min(5, s.getMaxRecommendations()));
        return new Snapshot(s.getStyle(), s.getResponseLength(), safeMax,
            s.isHighlightPromotions(), s.isSuggestRelatedProducts(), s.getVersion(), s.getUpdatedAt(), s.getUpdatedById());
    }
    private String summary(Snapshot s) {
        return s.style().getLabel() + " · " + s.responseLength().getLabel() + " · tối đa " + s.maxRecommendations()
            + " sản phẩm · Ưu đãi: " + (s.highlightPromotions() ? "Có" : "Không")
            + " · Liên quan: " + (s.suggestRelatedProducts() ? "Có" : "Không");
    }
    private Long adminId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details) || details.getUser().getId() == null
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền cấu hình AI.");
        return users.findById(details.getUser().getId()).filter(u -> "ADMIN".equals(u.getRole())).map(user -> user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản quản trị không còn hợp lệ."));
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    // Only these fixed, server-owned phrases are added to the model instructions.
    public static String modelGuidance(Snapshot s) {
        String style = switch (s.style()) {
            case FRIENDLY -> "Giọng văn thân thiện.";
            case PROFESSIONAL -> "Giọng văn chuyên nghiệp.";
            case CONCISE -> "Giọng văn trực tiếp, không rườm rà.";
        };
        String length = switch (s.responseLength()) {
            case SHORT -> "Phần lời dẫn một câu.";
            case MEDIUM -> "Phần lời dẫn hai câu ngắn.";
            case DETAILED -> "Phần lời dẫn có thêm giải thích chung.";
        };
        return "[ĐỊNH DẠNG CHO PHÉP] " + style + " " + length + " Chọn tối đa " + s.maxRecommendations()
            + " mã sản phẩm hợp lệ. Các quy tắc an toàn, nguồn dữ liệu sản phẩm và kiểm tra phía máy chủ luôn được giữ nguyên.";
    }
    public static String productIntroduction(Snapshot s, int relatedCount) {
        String base = s.style() == Style.FRIENDLY && s.responseLength() == Length.MEDIUM
            ? "Các sản phẩm phù hợp với yêu cầu của bạn. Giá, cấu hình và tình trạng hàng được lấy từ cửa hàng:"
            : switch (s.style()) {
                case FRIENDLY -> "Mình đã tìm được các sản phẩm khớp điều kiện bạn nêu:";
                case PROFESSIONAL -> "LAPZONE đã đối chiếu yêu cầu và danh mục sản phẩm hiện có:";
                case CONCISE -> "Gợi ý từ danh mục LAPZONE:";
            };
        if (s.responseLength() == Length.MEDIUM && s.style() != Style.FRIENDLY)
            base += " Giá và tình trạng hàng bên dưới được lấy từ hệ thống cửa hàng.";
        if (s.responseLength() == Length.DETAILED) base += " Thẻ bên dưới hiển thị giá, cấu hình và tồn kho lấy từ hệ thống cửa hàng. Bạn có thể mở trang chi tiết để xem thông tin mới nhất.";
        if (relatedCount > 0) base += " Có thêm sản phẩm cùng danh mục để bạn tham khảo.";
        return base;
    }
    public static String knowledgeIntroduction(Snapshot s) {
        if (s.style() == Style.FRIENDLY && s.responseLength() == Length.MEDIUM) return "";
        String prefix = s.style() == Style.PROFESSIONAL ? "Thông tin tham khảo từ LAPZONE:\n"
            : s.style() == Style.CONCISE ? "Tham khảo:\n" : "Mình gửi bạn thông tin tham khảo:\n";
        return s.responseLength() == Length.DETAILED
            ? prefix + "Nội dung được giữ nguyên theo bản đã xuất bản để không bỏ sót điều kiện quan trọng.\n" : prefix;
    }
}
