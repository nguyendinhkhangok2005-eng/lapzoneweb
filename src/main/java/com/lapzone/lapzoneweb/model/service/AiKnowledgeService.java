package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.entity.AiKnowledgeEntry.*;
import com.lapzone.lapzoneweb.model.entity.AiKnowledgeRevision.Category;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AiKnowledgeService {
    private final AiKnowledgeEntryRepository entries;
    private final AiKnowledgeRevisionRepository revisions;
    private final UserRepository users;
    private static final Set<String> STOP = Set.of("toi", "minh", "ban", "cho", "hoi", "ve", "co", "khong", "la", "va", "thi", "the", "nao", "nhu", "gi", "can", "duoc", "cua", "lapzone", "chinh", "sach", "quy", "dinh", "san", "pham", "tim", "duoi", "tren", "mua", "tu", "van", "goi", "y", "trieu");

    public AiKnowledgeService(AiKnowledgeEntryRepository entries, AiKnowledgeRevisionRepository revisions, UserRepository users) {
        this.entries = entries; this.revisions = revisions; this.users = users;
    }
    public record Draft(String title, String content, String keywords, String category) {}
    public record EntryView(Long id, String title, String content, String keywords, String category,
                            String categoryLabel, State state, int currentVersion, Integer publishedVersion) {}
    public record VersionView(int version, String title, String content, String category, String action, String createdAt) {}

    public Page<EntryView> list(int page) {
        admin(); checkPage(page);
        return revisions.latest(PageRequest.of(page, 20)).map(this::view);
    }
    public EntryView get(Long id) { admin(); return view(latest(entries.findById(id).orElseThrow(this::missing))); }
    public Page<VersionView> history(Long id, int page) {
        admin(); checkPage(page); entries.findById(id).orElseThrow(this::missing);
        return revisions.findByEntryIdOrderByVersionNoDesc(id, PageRequest.of(page, 20)).map(r -> new VersionView(
            r.getVersionNo(), r.getTitle(), r.getContent(), r.getCategory().getLabel(), r.getAction().getLabel(),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(r.getCreatedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")))));
    }
    @Transactional
    public Long create(Draft input) {
        User actor = admin(); Draft safe = validate(input);
        AiKnowledgeEntry e = entries.save(new AiKnowledgeEntry());
        snapshot(e, safe, Action.CREATE, actor);
        return e.getId();
    }
    @Transactional
    public void edit(Long id, int expectedVersion, Draft input) {
        User actor = admin(); Draft safe = validate(input);
        snapshot(editable(id, expectedVersion), safe, Action.EDIT, actor);
    }
    @Transactional
    public void transition(Long id, int expectedVersion, String actionText, boolean verified) {
        User actor = admin();
        Action action;
        try { action = Action.valueOf(actionText); } catch (RuntimeException ex) { throw bad("Thao tác không hợp lệ."); }
        if (!Set.of(Action.PUBLISH, Action.UNPUBLISH, Action.ARCHIVE).contains(action)) throw bad("Thao tác không hợp lệ.");
        AiKnowledgeEntry e = editable(id, expectedVersion);
        AiKnowledgeRevision current = latest(e);
        Draft draft = new Draft(current.getTitle(), current.getContent(), current.getKeywords(), current.getCategory().name());
        if (action == Action.PUBLISH) {
            if (!verified) throw bad("Vui lòng xác nhận đã kiểm tra nội dung trước khi xuất bản.");
            validate(draft); validateProductFacts(draft);
        }
        if (action == Action.UNPUBLISH && e.getPublishedVersion() == null) throw bad("Mục này chưa được xuất bản.");
        snapshot(e, draft, action, actor);
    }

    // Read-only reference retrieval: no model prompt or executable instruction is built from this text.
    public Optional<String> referenceAnswer(String question) {
        String normalized = normalize(question);
        List<String> terms = Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+"))
            .filter(t -> t.length() >= 2 && !STOP.contains(t)).distinct()
            .sorted(Comparator.comparingInt((String term) -> term.length()).reversed()).limit(6).toList();
        if (terms.isEmpty()) return Optional.empty();
        String[] t = new String[6]; Arrays.fill(t, "");
        for (int i = 0; i < terms.size(); i++) t[i] = terms.get(i);
        List<AiKnowledgeRevision> matches = revisions.publishedCandidates(State.PUBLISHED, t[0], t[1], t[2], t[3], t[4], t[5], PageRequest.of(0, 50))
            .stream().filter(r -> relevant(r, terms))
            .sorted(Comparator.<AiKnowledgeRevision>comparingInt(r -> score(r, terms)).reversed()
                .thenComparing(r -> r.getId())).limit(2).toList();
        if (matches.isEmpty()) return Optional.empty();
        return Optional.of("Kiến thức tham khảo của LAPZONE tại thời điểm trả lời:\n" + matches.stream()
            .map(r -> "\n" + r.getTitle() + " (phiên bản " + r.getVersionNo() + ")\n" + r.getContent())
            .collect(Collectors.joining("\n")));
    }

    public boolean isProductRequest(String question) {
        String q = normalize(question);
        return q.matches("(?s).*(?:\\b(?:gia|ton kho|con hang|het hang|cpu|gpu|ram|ssd|rtx|gaming)\\b|\\d+\\s*(?:trieu|tr|vnd|gb|tb)\\b).*")
            || q.matches("(?s).*(?:tim|goi y|tu van|chon|mua).*(?:laptop|may tinh|asus|acer|dell|lenovo|macbook).*");
    }

    private boolean relevant(AiKnowledgeRevision r, List<String> terms) {
        long matches = terms.stream().filter(t -> containsTerm(r.getSearchText(), t)).count();
        return matches >= Math.min(2, terms.size()) && (terms.size() > 1 || terms.getFirst().length() >= 6);
    }
    private int score(AiKnowledgeRevision r, List<String> terms) {
        String title = normalize(r.getTitle()), keywords = normalize(r.getKeywords());
        return terms.stream().mapToInt(t -> (containsTerm(title, t) ? 6 : 0) + (containsTerm(keywords, t) ? 4 : 0) + (containsTerm(r.getSearchText(), t) ? 1 : 0)).sum();
    }
    private boolean containsTerm(String text, String term) {
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(term) + "\\b").matcher(text).find();
    }
    private void snapshot(AiKnowledgeEntry e, Draft d, Action action, User actor) {
        e.advance(action);
        revisions.save(new AiKnowledgeRevision(e, d.title(), d.content(), d.keywords(),
            normalize(d.title() + " " + d.keywords() + " " + d.content()), Category.valueOf(d.category()), action, actor));
    }
    private AiKnowledgeEntry editable(Long id, int expected) {
        AiKnowledgeEntry e = entries.lock(id).orElseThrow(this::missing);
        if (e.getCurrentVersion() != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "Nội dung đã thay đổi. Vui lòng tải lại trang trước khi lưu.");
        if (e.getState() == State.ARCHIVED) throw bad("Mục đã lưu trữ không thể chỉnh sửa hoặc xuất bản. Hãy tạo mục mới nếu cần.");
        return e;
    }
    private AiKnowledgeRevision latest(AiKnowledgeEntry e) {
        return revisions.findByEntryIdAndVersionNo(e.getId(), e.getCurrentVersion()).orElseThrow(this::missing);
    }
    private EntryView view(AiKnowledgeRevision r) {
        AiKnowledgeEntry e = r.getEntry();
        return new EntryView(e.getId(), r.getTitle(), r.getContent(), r.getKeywords(), r.getCategory().name(), r.getCategory().getLabel(),
            e.getState(), e.getCurrentVersion(), e.getPublishedVersion());
    }
    private Draft validate(Draft input) {
        if (input == null) throw bad("Vui lòng nhập nội dung kiến thức.");
        String title = clean(input.title(), 160, true), content = clean(input.content(), 4000, true), keywords = clean(input.keywords(), 300, false);
        try { Category.valueOf(input.category()); } catch (RuntimeException ex) { throw bad("Danh mục kiến thức không hợp lệ."); }
        String text = normalize(title + " " + content + " " + keywords);
        if (text.matches("(?s).*(?:ignore (?:all |the )?(?:previous|system)|system prompt|bo qua.{0,40}(?:chi dan|huong dan)|ghi de.{0,30}(?:he thong|prompt)|javascript:|data:text/html).*") )
            throw bad("Kiến thức chỉ chứa dữ liệu tham khảo, không chứa chỉ lệnh điều khiển AI hoặc mã thực thi.");
        return new Draft(title, content, keywords, input.category());
    }
    private String clean(String raw, int max, boolean required) {
        String text = Normalizer.normalize(HtmlUtils.htmlUnescape(Objects.toString(raw, "")), Normalizer.Form.NFKC)
            .replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder();
        text.codePoints().filter(c -> Character.getType(c) != Character.FORMAT && (!Character.isISOControl(c) || c == '\n' || c == '\t')).forEach(out::appendCodePoint);
        text = out.toString().strip();
        if ((required && text.isBlank()) || text.length() > max) throw bad("Nội dung trống hoặc vượt độ dài cho phép.");
        if (text.matches("(?s).*<\\s*/?\\s*[a-zA-Z!][^>]*>.*")) throw bad("Chỉ nhập văn bản thuần, không dùng HTML hoặc mã nhúng.");
        return text;
    }
    private void validateProductFacts(Draft draft) {
        String q = normalize(draft.title() + "\n" + draft.content() + "\n" + draft.keywords());
        boolean hardwareValue = q.matches("(?s).*\\b\\d+\\s*(?:gb|tb|ghz|mhz)\\b.*");
        boolean namedHardware = q.matches("(?s).*\\b(?:laptop|may|san pham)\\b[^\\n.]{0,80}\\b(?:co|trang bi|cau hinh)\\b[^\\n.]{0,80}\\d+\\s*(?:gb|tb|ghz|mhz)\\b.*");
        if ((hardwareValue && (!"BUYING_GUIDE".equals(draft.category()) || namedHardware))
                || q.matches("(?s).*(?:product_detail|/assets/|/uploads/|https?://\\S+\\.(?:jpg|png|webp)|\\b(?:gia|price)\\s*(?:ban|la|:)?\\s*\\d|\\bton kho\\s*:?\\s*\\d|\\bcon\\s+\\d+\\s+(?:may|chiec|san pham)|\\bcon hang\\b|\\bhet hang\\b|\\bin stock\\b|\\bout of stock\\b).*" )
                || q.matches("(?s).*\\b(?:asus|acer|dell|hp|lenovo|msi|gigabyte|macbook)\\b[^\\n.]{0,100}\\b(?:ram|cpu|gpu|ssd|rtx)\\b.*"))
            throw bad("Không xuất bản giá, tồn kho, ảnh hoặc cấu hình của từng sản phẩm trong kiến thức. Các dữ liệu này phải lấy từ danh mục sản phẩm.");
    }
    private User admin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails p)
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý kiến thức AI.");
        return users.findById(p.getUser().getId()).filter(u -> "ADMIN".equals(u.getRole()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản quản trị không còn hợp lệ."));
    }
    private static String normalize(String value) {
        return AiProductRequirements.normalize(Objects.toString(value, ""));
    }
    private void checkPage(int page) { if (page < 0 || page > 1000) throw bad("Số trang không hợp lệ."); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục kiến thức."); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
