package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.AppointmentForm;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AppointmentService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> SUPPORT_TYPES = Set.of("Tư vấn mua laptop", "Tư vấn cấu hình laptop",
            "Bảo hành, sửa chữa", "Hỗ trợ kỹ thuật", "Khác");
    private final AppointmentRepository appointments;
    private final AppointmentManagementRepository management;
    private final AppointmentStatusHistoryRepository history;
    private final AppointmentBookingLockRepository bookingLocks;
    private final UserRepository users;
    private final AppointmentAvailability availability;
    private final Validator validator;
    private final Clock clock;

    @Autowired
    public AppointmentService(AppointmentRepository appointments, AppointmentManagementRepository management,
            AppointmentStatusHistoryRepository history, AppointmentBookingLockRepository bookingLocks,
            UserRepository users, AppointmentAvailability availability, Validator validator) {
        this(appointments, management, history, bookingLocks, users, availability, validator, Clock.system(ZONE));
    }
    AppointmentService(AppointmentRepository appointments, AppointmentManagementRepository management,
            AppointmentStatusHistoryRepository history, AppointmentBookingLockRepository bookingLocks,
            UserRepository users, AppointmentAvailability availability, Validator validator, Clock clock) {
        this.appointments = appointments; this.management = management; this.history = history;
        this.bookingLocks = bookingLocks; this.users = users; this.availability = availability;
        this.validator = validator; this.clock = clock.withZone(ZONE);
    }

    public record AppointmentView(Long id, Long customerId, String fullName, String phone, String email,
            String supportType, String showroom, LocalDate appointmentDate, LocalTime appointmentTime,
            String description, String status, String statusLabel, LocalDateTime createdAt, LocalDateTime updatedAt,
            LocalDateTime statusChangedAt, String statusReason, Integer durationMinutes,
            boolean canCancel, List<String> adminActions) {}
    public record AppointmentPage(List<AppointmentView> content, int number, int size, long totalElements,
            int totalPages, boolean hasNext, boolean hasPrevious) {}
    public record StatusChangeView(String previousStatus, String status, String statusLabel, String reason,
            String actorRole, LocalDateTime changedAt) {}
    public record Detail(AppointmentView appointment, List<StatusChangeView> history) {}

    /** Retains the /datlich entry point; ownership never comes from form fields. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Appointment createAppointment(AppointmentForm form) {
        if (form == null) throw new IllegalArgumentException("Vui lòng điền thông tin lịch hẹn.");
        var errors = validator.validate(form);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.stream().map(v -> v.getMessage()).sorted().findFirst().orElseThrow());
        if (!SUPPORT_TYPES.contains(form.getSupportType())) throw new IllegalArgumentException("Loại hỗ trợ không hợp lệ.");
        validateFuture(form.getAppointmentDate(), form.getAppointmentTime());
        User customer = actor(false, false);
        var slot = availability.resolve(form.getShowroom(), form.getAppointmentDate(), form.getAppointmentTime(), false, null);
        reserve(slot, form.getAppointmentDate(), form.getAppointmentTime(), null);
        validateFuture(form.getAppointmentDate(), form.getAppointmentTime());
        Appointment appointment = new Appointment();
        appointment.setFullName(form.getFullName().trim()); appointment.setPhone(form.getPhone().trim());
        appointment.setEmail(form.getEmail().trim()); appointment.setSupportType(form.getSupportType());
        appointment.setShowroom(slot.showroom()); appointment.setAppointmentDate(form.getAppointmentDate());
        appointment.setAppointmentTime(form.getAppointmentTime()); appointment.setDescription(clean(form.getDescription()));
        appointment.setStatus(AppointmentStatus.PENDING.name()); appointment.setCreatedAt(now());
        appointments.save(appointment);
        var metadata = metadata(appointment); metadata.setCustomer(customer); metadata.setDurationMinutes(slot.durationMinutes());
        recordChange(appointment, null, null, customer);
        return appointment;
    }

    public AppointmentPage customerAppointments(String status, int page, int size) {
        return list(actor(true, false), false, status, page, size);
    }
    public AppointmentPage adminAppointments(String status, int page, int size) {
        return list(actor(true, true), true, status, page, size);
    }
    public long adminPendingCount() {
        actor(true, true);
        return appointments.countByStatus(AppointmentStatus.PENDING.name());
    }
    public Detail customerDetail(Long id) {
        User customer = actor(true, false);
        return detail(appointments.accessible(id, customer.getId(), false).orElseThrow(this::missing), customer, false);
    }
    public Detail adminDetail(Long id) {
        User admin = actor(true, true);
        return detail(appointments.accessible(id, admin.getId(), true).orElseThrow(this::missing), admin, true);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Detail changeStatus(Long id, String targetStatus, String reason, String assignedShowroom) {
        User admin = actor(true, true);
        AppointmentStatus target = AppointmentStatus.parse(targetStatus);
        String explanation = reason(reason, target == AppointmentStatus.REJECTED);
        String assignment = clean(assignedShowroom);
        if (assignment != null && assignment.length() > 200) throw new IllegalArgumentException("Tên cửa hàng quá dài.");
        AppointmentAvailability.Slot slot = null;
        if (target == AppointmentStatus.CONFIRMED) {
            var schedule = appointments.schedule(id).orElseThrow(this::missing);
            if (assignment != null && clean(schedule.getShowroom()) != null && !assignment.equalsIgnoreCase(schedule.getShowroom().trim()))
                throw new IllegalArgumentException("Không thể đổi cửa hàng đã chọn bằng thao tác xác nhận.");
            validateFuture(schedule.getAppointmentDate(), schedule.getAppointmentTime());
            slot = availability.resolve(assignment == null ? schedule.getShowroom() : assignment,
                    schedule.getAppointmentDate(), schedule.getAppointmentTime(), true, schedule.getDurationMinutes());
            reserve(slot, schedule.getAppointmentDate(), schedule.getAppointmentTime(), id);
        } else if (assignment != null) {
            throw new IllegalArgumentException("Chỉ được chọn cửa hàng khi xác nhận lịch hẹn chưa có cửa hàng.");
        }
        Appointment appointment = appointments.lockById(id).orElseThrow(this::missing);
        String previous = appointment.getStatus();
        boolean allowed = (AppointmentStatus.PENDING.name().equals(previous)
                && (target == AppointmentStatus.CONFIRMED || target == AppointmentStatus.REJECTED))
                || (AppointmentStatus.CONFIRMED.name().equals(previous) && target == AppointmentStatus.COMPLETED);
        if (!allowed) throw conflict("Không thể chuyển trạng thái lịch hẹn theo yêu cầu.");
        if (target == AppointmentStatus.CONFIRMED) {
            validateFuture(appointment.getAppointmentDate(), appointment.getAppointmentTime());
            if (slot == null) throw new IllegalStateException("Chưa kiểm tra khung giờ xác nhận.");
            appointment.setShowroom(slot.showroom()); metadata(appointment).setDurationMinutes(slot.durationMinutes());
        }
        if (target == AppointmentStatus.COMPLETED && appointmentAt(appointment).isAfter(now()))
            throw conflict("Chỉ được hoàn thành lịch hẹn khi đã đến thời gian hẹn.");
        appointment.setStatus(target.name()); recordChange(appointment, previous, explanation, admin);
        return detail(appointment, admin, true);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Detail cancel(Long id, String reason) {
        User customer = actor(true, false);
        String explanation = reason(reason, true);
        Appointment appointment = appointments.lockById(id).orElseThrow(this::missing);
        if (!owned(appointment, customer)) throw missing();
        if (!cancellable(appointment)) throw conflict("Chỉ được hủy lịch đang chờ xác nhận và chưa đến giờ hẹn.");
        String previous = appointment.getStatus(); appointment.setStatus(AppointmentStatus.CANCELLED.name());
        recordChange(appointment, previous, explanation, customer);
        return detail(appointment, customer, false);
    }

    private AppointmentPage list(User user, boolean admin, String status, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("Phân trang không hợp lệ.");
        String filter = clean(status);
        if (filter != null) filter = AppointmentStatus.parse(filter).name();
        var result = appointments.accessiblePage(user.getId(), admin, filter,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new AppointmentPage(result.map(a -> view(a, user, admin)).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious());
    }
    private Detail detail(Appointment appointment, User user, boolean admin) {
        return new Detail(view(appointment, user, admin), history.findByAppointmentIdOrderByIdAsc(appointment.getId()).stream()
                .map(h -> new StatusChangeView(h.getPreviousStatus(), h.getStatus(), AppointmentStatus.labelFor(h.getStatus()),
                        h.getReason(), h.getActorRole(), h.getChangedAt())).toList());
    }
    private AppointmentView view(Appointment a, User actor, boolean admin) {
        var m = a.getManagement();
        List<String> actions = !admin ? List.of() : AppointmentStatus.PENDING.name().equals(a.getStatus())
                ? (appointmentAt(a).isAfter(now()) ? List.of("CONFIRMED", "REJECTED") : List.of("REJECTED"))
                : AppointmentStatus.CONFIRMED.name().equals(a.getStatus()) && !appointmentAt(a).isAfter(now())
                ? List.of("COMPLETED") : List.of();
        return new AppointmentView(a.getId(), m == null || m.getCustomer() == null ? null : m.getCustomer().getId(),
                a.getFullName(), a.getPhone(), a.getEmail(), a.getSupportType(), a.getShowroom(), a.getAppointmentDate(),
                a.getAppointmentTime(), a.getDescription(), a.getStatus(), AppointmentStatus.labelFor(a.getStatus()), a.getCreatedAt(),
                m == null ? a.getCreatedAt() : m.getUpdatedAt(), m == null ? null : m.getStatusChangedAt(),
                m == null ? null : m.getStatusReason(), m == null ? null : m.getDurationMinutes(),
                owned(a, actor) && cancellable(a), actions);
    }
    private AppointmentManagement metadata(Appointment a) {
        if (a.getManagement() == null) {
            var m = new AppointmentManagement(); m.setAppointment(a); a.setManagement(m);
        }
        return a.getManagement();
    }
    private void recordChange(Appointment a, String previous, String reason, User actor) {
        LocalDateTime changed = now();
        var m = metadata(a); m.setUpdatedAt(changed); m.setStatusChangedAt(changed); m.setStatusReason(reason); management.save(m);
        var event = new AppointmentStatusHistory(); event.setAppointment(a); event.setPreviousStatus(previous);
        event.setStatus(a.getStatus()); event.setReason(reason); event.setChangedBy(actor);
        event.setActorRole(actor == null ? "GUEST" : actor.getRole()); event.setChangedAt(changed); history.save(event);
    }

    private void reserve(AppointmentAvailability.Slot slot, LocalDate date, LocalTime time, Long exclude) {
        if (slot.capacity() == null) return;
        bookingLocks.acquire(lockKey(slot.showroom(), date));
        LocalDateTime start = LocalDateTime.of(date, time), end = start.plusMinutes(slot.durationMinutes());
        var events = new TreeMap<LocalDateTime, Integer>();
        for (Appointment existing : appointments.lockActiveBookings(date, slot.aliases())) {
            if (existing.getId().equals(exclude)) continue;
            LocalDateTime otherStart = appointmentAt(existing);
            Integer saved = existing.getManagement() == null ? null : existing.getManagement().getDurationMinutes();
            LocalDateTime otherEnd = otherStart.plusMinutes(saved == null ? slot.durationMinutes() : saved);
            if (!otherStart.isBefore(end) || !otherEnd.isAfter(start)) continue;
            addEvent(events, otherStart.isBefore(start) ? start : otherStart, 1);
            addEvent(events, otherEnd.isAfter(end) ? end : otherEnd, -1);
        }
        int concurrent = 0;
        for (var event : events.entrySet()) {
            concurrent += event.getValue();
            if (concurrent >= slot.capacity()) throw conflict("Khung giờ đã đủ lịch hẹn tại cửa hàng. Vui lòng chọn thời gian khác.");
        }
    }
    private void addEvent(TreeMap<LocalDateTime, Integer> events, LocalDateTime time, int delta) {
        events.compute(time, (key, count) -> count == null ? delta : count + delta);
    }
    private String lockKey(String showroom, LocalDate date) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (showroom.toLowerCase(Locale.ROOT) + "|" + date).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private User actor(boolean required, boolean admin) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            if (required) throw new AccessDeniedException("Vui lòng đăng nhập để xem lịch hẹn.");
            return null;
        }
        if (!(auth.getPrincipal() instanceof CustomUserDetails details) || details.getUser().getId() == null)
            throw new AccessDeniedException("Tài khoản không hợp lệ.");
        User user = users.findById(details.getUser().getId()).orElseThrow(() -> new AccessDeniedException("Tài khoản không còn khả dụng."));
        if (admin && (!"ADMIN".equals(user.getRole()) || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))))
            throw new AccessDeniedException("Bạn không có quyền quản lý lịch hẹn.");
        return user;
    }
    private boolean owned(Appointment a, User user) {
        return user != null && a.getManagement() != null && a.getManagement().getCustomer() != null
                && user.getId().equals(a.getManagement().getCustomer().getId());
    }
    private boolean cancellable(Appointment a) { return AppointmentStatus.PENDING.name().equals(a.getStatus()) && appointmentAt(a).isAfter(now()); }
    private LocalDateTime appointmentAt(Appointment a) { return LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime()); }
    private void validateFuture(LocalDate date, LocalTime time) {
        if (date == null || time == null || date.getYear() < 1000 || date.getYear() > 9999 || time.getSecond() != 0 || time.getNano() != 0)
            throw new IllegalArgumentException("Ngày và giờ hẹn không hợp lệ; vui lòng chọn giờ chính xác đến phút.");
        if (!LocalDateTime.of(date, time).isAfter(now())) throw new IllegalArgumentException("Ngày và giờ hẹn phải ở thời điểm trong tương lai.");
    }
    private String reason(String value, boolean required) {
        String result = clean(value);
        if ((required && result == null) || (result != null && (result.length() > 2000 || result.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))))
            throw new IllegalArgumentException("Lý do phải có từ 1 đến 2.000 ký tự hợp lệ.");
        return result;
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn hoặc bạn không có quyền truy cập."); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
