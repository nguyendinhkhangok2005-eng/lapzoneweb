package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.AppointmentForm;
import com.lapzone.lapzoneweb.model.entity.Appointment;
import com.lapzone.lapzoneweb.model.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void createsPendingAppointmentAndTrimsOptionalFields() {
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AppointmentForm form = validForm();
        form.setShowroom("  LAPZONE Hà Nội  ");
        form.setDescription("  Cần tư vấn cấu hình máy  ");

        Appointment appointment = appointmentService.createAppointment(form);

        assertEquals("Nguyễn An", appointment.getFullName());
        assertEquals("Tư vấn mua laptop", appointment.getSupportType());
        assertEquals("LAPZONE Hà Nội", appointment.getShowroom());
        assertEquals("Cần tư vấn cấu hình máy", appointment.getDescription());
        assertEquals("PENDING", appointment.getStatus());
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void rejectsAppointmentInThePastWithoutSaving() {
        AppointmentForm form = validForm();
        form.setAppointmentDate(LocalDate.now().minusDays(1));

        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(form));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void rejectsUnsupportedTypeWithoutSaving() {
        AppointmentForm form = validForm();
        form.setSupportType("Loại không có trong danh sách");

        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(form));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    private AppointmentForm validForm() {
        AppointmentForm form = new AppointmentForm();
        form.setFullName("Nguyễn An");
        form.setPhone("0912345678");
        form.setEmail("an@example.com");
        form.setSupportType("Tư vấn mua laptop");
        form.setAppointmentDate(LocalDate.now().plusDays(1));
        form.setAppointmentTime(LocalTime.of(10, 30));
        return form;
    }
}
