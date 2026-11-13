package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
@Transactional(readOnly = true)
public class AiInspectionRunStore {
    private final AiInspectionRunRepository runs;
    private final UserRepository users;
    private final Clock clock;
    @Autowired
    public AiInspectionRunStore(AiInspectionRunRepository runs, UserRepository users) { this(runs, users, Clock.systemUTC()); }
    AiInspectionRunStore(AiInspectionRunRepository runs, UserRepository users, Clock clock) { this.runs = runs; this.users = users; this.clock = clock; }
    public record Reservation(Long id, boolean execute) {}
    public record RunView(Long id, String question, String preset, String state, String startedAt, String finishedAt) {}
    public record Detail(RunView run, AiRecommendationValidator.Result result, String error) {}

    @Transactional
    public Reservation reserve(String question, String preset, String token) {
        User actor = actor(true); // Serializes reservations for one admin across application instances.
        var duplicate = runs.findByRequestToken(token);
        if (duplicate.isPresent()) {
            var run = duplicate.get();
            if (run.getAdmin() == null || !actor.getId().equals(run.getAdmin().getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã yêu cầu đã được dùng. Vui lòng mở lại trang kiểm tra.");
            return new Reservation(run.getId(), false);
        }
        Instant now = clock.instant();
        var latest = runs.findTopByAdminIdOrderByStartedAtDescIdDesc(actor.getId());
        if (latest.isPresent() && (latest.get().getStartedAt().isAfter(now.minusSeconds(30))
                || (latest.get().getState() == AiInspectionRun.State.RUNNING && latest.get().getStartedAt().isAfter(now.minusSeconds(120)))))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bài kiểm tra đang chạy hoặc vừa chạy xong. Vui lòng đợi trước khi gửi câu hỏi mới.");
        if (runs.countByAdminIdAndStartedAtGreaterThanEqual(actor.getId(), now.minusSeconds(3600)) >= 10)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Đã đạt giới hạn 10 bài kiểm tra mỗi giờ. Vui lòng thử lại sau.");
        var run = runs.save(new AiInspectionRun(actor, token, question, preset, now));
        return new Reservation(run.getId(), true);
    }
    @Transactional
    public void complete(Long id, AiRecommendationValidator.Result result) {
        var run = runs.lock(id).orElseThrow(this::missing);
        try { run.finish(new ObjectMapper().writeValueAsString(result), clock.instant()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Không thể lưu kết quả kiểm tra.", ex); }
    }
    @Transactional
    public void fail(Long id) { runs.lock(id).orElseThrow(this::missing).fail(clock.instant()); }

    public Page<RunView> list(int page) {
        actor(false);
        if (page < 0 || page > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số trang không hợp lệ.");
        return runs.findAllByOrderByStartedAtDescIdDesc(PageRequest.of(page, 20)).map(this::view);
    }
    public Detail get(Long id) {
        actor(false); var run = runs.findById(id).orElseThrow(this::missing);
        AiRecommendationValidator.Result result = null;
        if (run.getResultJson() != null) {
            try { result = new ObjectMapper().readValue(run.getResultJson(), AiRecommendationValidator.Result.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được kết quả kiểm tra đã lưu.");
            }
        }
        return new Detail(view(run), result, run.getError());
    }
    private RunView view(AiInspectionRun r) {
        String preset = java.util.Arrays.stream(AdminAiInspectionService.Preset.values()).filter(p -> p.name().equals(r.getPreset()))
            .map(p -> p.getLabel()).findFirst().orElse("Tùy chỉnh");
        return new RunView(r.getId(), r.getQuestion(), preset, r.getState().getLabel(), format(r.getStartedAt()), format(r.getFinishedAt()));
    }
    private String format(Instant instant) { return instant == null ? "Chưa hoàn tất" : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))); }
    private User actor(boolean lock) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails p) || p.getUser().getId() == null
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền kiểm tra gợi ý AI.");
        return (lock ? users.lockForAiInspection(p.getUser().getId()) : users.findById(p.getUser().getId()))
            .filter(u -> "ADMIN".equals(u.getRole())).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản quản trị không còn hợp lệ."));
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài kiểm tra."); }
}
