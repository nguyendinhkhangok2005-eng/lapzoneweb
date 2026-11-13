package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.config.StoreDirectoryProperties;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Uses the existing showroom directory; missing limits never become invented limits. */
@Component
public class AppointmentAvailability {
    private static final Pattern DAILY_HOURS = Pattern.compile("^(\\d{1,2}:\\d{2})\\s*[-–—]\\s*(\\d{1,2}:\\d{2})$");
    private final StoreDirectoryProperties stores;
    public AppointmentAvailability(StoreDirectoryProperties stores) { this.stores = stores; }

    public record Slot(String showroom, Integer durationMinutes, Integer capacity, List<String> aliases) {}

    public Slot resolve(String requested, LocalDate date, LocalTime time, boolean confirmation, Integer savedDuration) {
        String showroom = clean(requested);
        var branches = stores.getBranches().stream().filter(Objects::nonNull)
                .filter(b -> clean(b.getName()) != null || clean(b.getAddress()) != null).toList();
        if (branches.isEmpty()) return new Slot(showroom, savedDuration, null, List.of());
        if (showroom == null) {
            if (confirmation) throw new IllegalArgumentException("Vui lòng chọn cửa hàng đã cấu hình trước khi xác nhận lịch hẹn.");
            return new Slot(null, null, null, List.of()); // No preference: pending request, not a reserved showroom slot.
        }
        var matches = branches.stream().filter(b -> same(showroom, b.getName()) || same(showroom, b.getAddress())).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Cửa hàng không hợp lệ hoặc chưa xác định rõ.");
        var branch = matches.getFirst();
        if (!branch.isAppointmentsEnabled()) throw new IllegalArgumentException("Cửa hàng hiện không nhận lịch hẹn.");
        if (branch.getAppointmentDays() != null && !branch.getAppointmentDays().contains(date.getDayOfWeek()))
            throw new IllegalArgumentException("Cửa hàng không nhận lịch hẹn vào ngày đã chọn.");
        Integer duration = savedDuration == null ? branch.getAppointmentDurationMinutes() : savedDuration;
        Integer capacity = branch.getAppointmentCapacity();
        if ((duration != null && (duration < 1 || duration > 1440)) || (capacity != null && (capacity < 1 || duration == null)))
            throw new IllegalArgumentException("Cấu hình sức chứa và thời lượng lịch hẹn của cửa hàng chưa hợp lệ.");
        LocalTime opens = branch.getAppointmentOpensAt(), closes = branch.getAppointmentClosesAt();
        if (opens == null && closes == null && clean(branch.getOpeningHours()) != null) {
            var match = DAILY_HOURS.matcher(branch.getOpeningHours().trim());
            if (!match.matches()) throw new IllegalArgumentException("Giờ mở cửa cần được cấu hình rõ trước khi nhận lịch hẹn.");
            try {
                opens = LocalTime.parse(padHour(match.group(1))); closes = LocalTime.parse(padHour(match.group(2)));
            } catch (DateTimeException e) { throw new IllegalArgumentException("Giờ mở cửa của cửa hàng chưa hợp lệ."); }
        }
        LocalDateTime start = LocalDateTime.of(date, time), end = start.plusMinutes(duration == null ? 0 : duration);
        if (!end.toLocalDate().equals(date)) throw new IllegalArgumentException("Lịch hẹn phải kết thúc trong ngày đã chọn.");
        if (opens != null || closes != null) {
            if (opens == null || closes == null || !opens.isBefore(closes))
                throw new IllegalArgumentException("Giờ mở cửa của cửa hàng chưa hợp lệ.");
            if (time.isBefore(opens) || !time.isBefore(closes) || end.toLocalTime().isAfter(closes))
                throw new IllegalArgumentException("Thời gian hẹn nằm ngoài giờ mở cửa của cửa hàng.");
        }
        String canonical = clean(branch.getName()) == null ? clean(branch.getAddress()) : clean(branch.getName());
        if (canonical == null || canonical.length() > 200) throw new IllegalArgumentException("Tên cửa hàng cấu hình không hợp lệ.");
        var aliases = new LinkedHashSet<String>();
        if (clean(branch.getName()) != null) aliases.add(branch.getName().trim().toLowerCase(Locale.ROOT));
        if (clean(branch.getAddress()) != null) aliases.add(branch.getAddress().trim().toLowerCase(Locale.ROOT));
        return new Slot(canonical, duration, capacity, List.copyOf(aliases));
    }

    private String padHour(String time) { return time.indexOf(':') == 1 ? "0" + time : time; }
    private boolean same(String first, String second) { return second != null && first.equalsIgnoreCase(second.trim()); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
