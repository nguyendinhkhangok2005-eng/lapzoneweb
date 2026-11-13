package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.AppointmentForm;
import com.lapzone.lapzoneweb.model.entity.Appointment;
import com.lapzone.lapzoneweb.model.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.config.StoreDirectoryProperties;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    private AppointmentService appointmentService;
    @Mock AppointmentManagementRepository management;
    @Mock AppointmentStatusHistoryRepository history;
    @Mock AppointmentBookingLockRepository locks;
    @Mock UserRepository users;

    @BeforeEach void setup() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        appointmentService = new AppointmentService(appointmentRepository, management, history, locks, users,
                new AppointmentAvailability(new StoreDirectoryProperties()),
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
    }

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

    @Test void validatesFutureInVietnamTimeAtTheUtcDateBoundary() {
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-11-13T17:30:00Z"), java.time.ZoneOffset.UTC);
        appointmentService = new AppointmentService(appointmentRepository, management, history, locks, users,
                new AppointmentAvailability(new StoreDirectoryProperties()),
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(), clock);
        var form = validForm(); form.setAppointmentDate(LocalDate.of(2026, 11, 14)); form.setAppointmentTime(LocalTime.of(0, 15));
        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(form));
        form.setAppointmentTime(LocalTime.of(1, 0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(java.time.LocalDateTime.of(2026, 11, 14, 0, 30), appointmentService.createAppointment(form).getCreatedAt());
    }

    @Test void validatesContactAndSizeConstraintsEvenWhenCalledWithoutMvc() {
        var invalidEmail = validForm(); invalidEmail.setEmail("không-phải-email");
        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(invalidEmail));
        var oversized = validForm(); oversized.setDescription("x".repeat(5001));
        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(oversized));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test void missingConfigurationNeverInventsCapacityOrOpeningHours() {
        var availability = new AppointmentAvailability(new StoreDirectoryProperties());
        var date = LocalDate.of(2026, 11, 20);
        var slot = availability.resolve("  Cửa hàng mong muốn  ", date, LocalTime.of(22, 0), false, null);
        assertEquals("Cửa hàng mong muốn", slot.showroom());
        org.junit.jupiter.api.Assertions.assertNull(slot.capacity()); org.junit.jupiter.api.Assertions.assertNull(slot.durationMinutes());
        org.junit.jupiter.api.Assertions.assertNull(availability.resolve(null, date, LocalTime.NOON, false, null).showroom());
    }

    @Test void structuredHoursOverrideProseAndRequireTheWholeDurationToFit() {
        var stores = new StoreDirectoryProperties(); var availability = new AppointmentAvailability(stores);
        var branch = new StoreDirectoryProperties.Branch(); branch.setName("Cửa hàng A");
        branch.setOpeningHours("Từ thứ hai tới thứ sáu"); branch.setAppointmentDurationMinutes(30);
        branch.setAppointmentOpensAt(LocalTime.of(9, 0)); branch.setAppointmentClosesAt(LocalTime.of(17, 0));
        stores.setBranches(java.util.List.of(branch)); var date = LocalDate.of(2026, 11, 20);
        org.junit.jupiter.api.Assertions.assertNotNull(availability.resolve("cửa hàng a", date, LocalTime.of(16, 30), false, null));
        assertThrows(IllegalArgumentException.class, () -> availability.resolve("Cửa hàng A", date, LocalTime.of(16, 45), false, null));
    }

    @Test void capacityWithoutDurationAndAmbiguousShowroomsAreRejected() {
        var stores = new StoreDirectoryProperties(); var availability = new AppointmentAvailability(stores);
        var branch = new StoreDirectoryProperties.Branch(); branch.setName("Cửa hàng A"); branch.setAppointmentCapacity(1);
        stores.setBranches(java.util.List.of(branch)); var date = LocalDate.of(2026, 11, 20);
        assertThrows(IllegalArgumentException.class, () -> availability.resolve("Cửa hàng A", date, LocalTime.NOON, false, null));
        branch.setAppointmentDurationMinutes(30); stores.setBranches(java.util.List.of(branch, branch));
        assertThrows(IllegalArgumentException.class, () -> availability.resolve("Cửa hàng A", date, LocalTime.NOON, false, null));
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
