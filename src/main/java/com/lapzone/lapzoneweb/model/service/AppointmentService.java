package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.AppointmentForm;
import com.lapzone.lapzoneweb.model.entity.Appointment;
import com.lapzone.lapzoneweb.model.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AppointmentService {

    private static final Set<String> SUPPORT_TYPES = Set.of(
            "Tư vấn mua laptop",
            "Tư vấn cấu hình laptop",
            "Bảo hành, sửa chữa",
            "Hỗ trợ kỹ thuật",
            "Khác");

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public Appointment createAppointment(AppointmentForm form) {
        if (form == null
                || isBlank(form.getFullName())
                || isBlank(form.getPhone())
                || isBlank(form.getEmail())
                || isBlank(form.getSupportType())
                || form.getAppointmentDate() == null
                || form.getAppointmentTime() == null) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ các thông tin bắt buộc.");
        }
        if (!SUPPORT_TYPES.contains(form.getSupportType())) {
            throw new IllegalArgumentException("Loại hỗ trợ không hợp lệ.");
        }
        if (!LocalDateTime.of(form.getAppointmentDate(), form.getAppointmentTime()).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Ngày và giờ hẹn phải ở thời điểm trong tương lai.");
        }

        Appointment appointment = new Appointment();
        appointment.setFullName(form.getFullName().trim());
        appointment.setPhone(form.getPhone().trim());
        appointment.setEmail(form.getEmail().trim());
        appointment.setSupportType(form.getSupportType());
        appointment.setShowroom(trimToNull(form.getShowroom()));
        appointment.setAppointmentDate(form.getAppointmentDate());
        appointment.setAppointmentTime(form.getAppointmentTime());
        appointment.setDescription(trimToNull(form.getDescription()));
        appointment.setStatus("PENDING");
        return appointmentRepository.save(appointment);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
