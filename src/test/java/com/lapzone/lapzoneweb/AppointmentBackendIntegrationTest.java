package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.config.StoreDirectoryProperties;
import com.lapzone.lapzoneweb.model.dto.AppointmentForm;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import com.lapzone.lapzoneweb.model.service.AppointmentService;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Every write goes to this isolated H2 database, never the application's MySQL database. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:appointment_backend;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.config.import=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false",
    "lapzone.stores.branches[0].name=LAPZONE Kiểm thử", "lapzone.stores.branches[0].address=Cửa hàng kiểm thử",
    "lapzone.stores.branches[0].opening-hours=08:00–20:00",
    "lapzone.stores.branches[0].appointment-capacity=2", "lapzone.stores.branches[0].appointment-duration-minutes=30"
})
@AutoConfigureMockMvc
class AppointmentBackendIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppointmentService service;
    @Autowired AppointmentRepository appointments;
    @Autowired AppointmentManagementRepository management;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AppointmentStatusHistoryRepository history;
    @Autowired AppointmentBookingLockRepository locks;
    @Autowired UserRepository users;
    @Autowired StoreDirectoryProperties stores;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    User customer, other, admin;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        history.deleteAllInBatch(); management.deleteAllInBatch(); appointments.deleteAllInBatch(); locks.deleteAllInBatch();
        customer = user("USER"); other = user("USER"); admin = user("ADMIN");
        var branch = stores.getBranches().getFirst();
        branch.setAppointmentCapacity(2); branch.setAppointmentDurationMinutes(30); branch.setAppointmentDays(null);
        branch.setAppointmentsEnabled(true); branch.setOpeningHours("08:00–20:00");
        branch.setAppointmentOpensAt(null); branch.setAppointmentClosesAt(null);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    User user(String role) {
        User u = new User(); u.setFullName("Khách kiểm thử"); u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPhone("0900000000"); u.setPassword("KHÔNG ĐƯỢC XUẤT"); u.setRole(role); return users.saveAndFlush(u);
    }
    MockHttpSession session(User user) {
        var s = new MockHttpSession(); var p = new CustomUserDetails(user);
        s.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities())));
        return s;
    }
    void auth(User user) {
        SecurityContextHolder.clearContext();
        if (user != null) {
            var p = new CustomUserDetails(user);
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        }
    }
    LocalDate date() { return LocalDate.now(AppointmentService.ZONE).plusDays(7); }
    AppointmentForm form(String time) {
        var f = new AppointmentForm(); f.setFullName("Khách đặt lịch"); f.setPhone(customer.getPhone());
        f.setEmail(customer.getEmail()); f.setSupportType("Tư vấn mua laptop"); f.setShowroom("LAPZONE Kiểm thử");
        f.setAppointmentDate(date()); f.setAppointmentTime(LocalTime.parse(time)); return f;
    }
    Long book(User user, String time) {
        auth(user);
        try { return service.createAppointment(form(time)).getId(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    CsrfToken csrf(MockHttpSession session) throws Exception {
        return (CsrfToken) mvc.perform(get("/datlich").session(session)).andExpect(status().isOk()).andReturn().getRequest().getAttribute("_csrf");
    }
    ResultActions change(Long id, String body, MockHttpSession session) throws Exception {
        var token = csrf(session);
        return mvc.perform(post("/admin/appointments/" + id + "/status").session(session)
                .header(token.getHeaderName(), token.getToken()).contentType("application/json").content(body));
    }
    ResultActions cancel(Long id, String body, MockHttpSession session) throws Exception {
        var token = csrf(session);
        return mvc.perform(post("/api/appointments/" + id + "/cancel").session(session)
                .header(token.getHeaderName(), token.getToken()).contentType("application/json").content(body));
    }
    ResultActions adminForm(Long id, String target, String reason, MockHttpSession session) throws Exception {
        var token = csrf(session);
        return mvc.perform(post("/admin/support/appointments/" + id + "/status").session(session)
                .param(token.getParameterName(), token.getToken()).param("status", target).param("reason", reason));
    }

    @Test void datlichPersistsPrincipalOwnershipAndIgnoresSpoofedOwnerFields() throws Exception {
        var s = session(customer); var token = csrf(s);
        mvc.perform(post("/datlich").session(s).param(token.getParameterName(), token.getToken())
                .param("fullName", "Nguyễn An").param("phone", "0912345678").param("email", "contact@test.local")
                .param("supportType", "Tư vấn mua laptop").param("appointmentDate", date().toString())
                .param("appointmentTime", "10:00").param("showroom", "Cửa hàng kiểm thử")
                .param("customerId", other.getId().toString()).param("userId", other.getId().toString()).param("status", "COMPLETED"))
            .andExpect(redirectedUrl("/datlich")).andExpect(flash().attributeExists("appointmentSuccess"));
        mvc.perform(get("/api/appointments").session(s)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].customerId").value(customer.getId()))
            .andExpect(jsonPath("$.content[0].status").value("PENDING")).andExpect(jsonPath("$.content[0].statusLabel").value("Chờ xác nhận"))
            .andExpect(jsonPath("$.content[0].showroom").value("LAPZONE Kiểm thử"))
            .andExpect(content().string(not(containsString(customer.getPassword()))));
        var event = history.findAll().getFirst();
        assertEquals(customer.getId(), event.getChangedBy().getId()); assertNotNull(event.getChangedAt());
    }

    @Test void guestAndLegacyMatchingContactsAreNeverClaimedByAccount() throws Exception {
        Long guest = book(null, "10:00");
        var legacy = new Appointment(); legacy.setFullName("Lịch cũ"); legacy.setEmail(customer.getEmail()); legacy.setPhone(customer.getPhone());
        legacy.setSupportType("Khác"); legacy.setAppointmentDate(date()); legacy.setAppointmentTime(LocalTime.of(14, 0));
        legacy.setStatus("PENDING"); appointments.saveAndFlush(legacy);
        mvc.perform(get("/api/appointments").session(session(customer))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        for (Long id : List.of(guest, legacy.getId())) {
            mvc.perform(get("/api/appointments/" + id).session(session(customer))).andExpect(status().isNotFound());
            cancel(id, "{\"reason\":\"Trùng thông tin liên hệ\"}", session(customer)).andExpect(status().isNotFound());
            mvc.perform(get("/admin/appointments/" + id).session(session(admin))).andExpect(status().isOk());
        }
        assertFalse(management.existsById(legacy.getId()));
        change(legacy.getId(), "{\"status\":\"REJECTED\",\"reason\":\"Đã liên hệ riêng\"}", session(admin)).andExpect(status().isOk());
        assertNull(management.findById(legacy.getId()).orElseThrow().getCustomer());
        assertEquals("Lịch cũ", appointments.findById(legacy.getId()).orElseThrow().getFullName());
    }

    @Test void foreignUserCannotReadOrCancelOrChooseAnotherOwnerInList() throws Exception {
        Long id = book(customer, "10:00"); var s = session(other);
        mvc.perform(get("/api/appointments").session(s).param("customerId", customer.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/appointments/" + id).session(s)).andExpect(status().isNotFound());
        cancel(id, "{\"reason\":\"Hủy thay người khác\"}", s).andExpect(status().isNotFound());
        assertEquals("PENDING", appointments.findById(id).orElseThrow().getStatus()); assertEquals(1, history.count());
    }

    @Test void authenticationAdminRolesAndCsrfProtectEveryMutation() throws Exception {
        Long id = book(customer, "10:00");
        mvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/appointments").session(session(customer))).andExpect(status().isForbidden());
        change(id, "{\"status\":\"CONFIRMED\"}", session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/appointments/" + id + "/status").session(session(admin)).contentType("application/json")
                .content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments/" + id + "/cancel").session(session(customer)).contentType("application/json")
                .content("{\"reason\":\"Bận\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/datlich").session(session(customer))).andExpect(status().isForbidden());
        var forged = new MockHttpSession(); forged.setAttribute("currentUser", customer);
        mvc.perform(get("/api/appointments").session(forged)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/csrf").session(session(customer))).andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void staleAdminRoleIsRecheckedAgainstActualAccount() throws Exception {
        var s = session(admin); var stored = users.findById(admin.getId()).orElseThrow(); stored.setRole("USER"); users.saveAndFlush(stored);
        mvc.perform(get("/admin/appointments").session(s)).andExpect(status().isForbidden());
    }

    @Test void adminTransitionsRequireReasonAndRejectTerminalRewrites() throws Exception {
        Long id = book(customer, "10:00"); var s = session(admin);
        change(id, "{\"status\":\"COMPLETED\"}", s).andExpect(status().isConflict());
        change(id, "{\"status\":\"REJECTED\"}", s).andExpect(status().isBadRequest());
        change(id, "{\"status\":\"REJECTED\",\"reason\":\"  Cần kiểm tra máy trước  \"}", s)
            .andExpect(status().isOk()).andExpect(jsonPath("$.appointment.status").value("REJECTED"))
            .andExpect(jsonPath("$.appointment.statusReason").value("Cần kiểm tra máy trước"))
            .andExpect(jsonPath("$.appointment.statusChangedAt").isNotEmpty()).andExpect(jsonPath("$.history.length()").value(2));
        change(id, "{\"status\":\"CONFIRMED\"}", s).andExpect(status().isConflict());
        change(id, "{\"status\":\"CANCELLED\"}", s).andExpect(status().isConflict());
        cancel(id, "{\"reason\":\"Đổi ý\"}", session(customer)).andExpect(status().isConflict());
        assertEquals(2, history.count());
    }

    @Test void confirmationCannotDoubleCountItselfAndCompletionWaitsForAppointmentTime() throws Exception {
        stores.getBranches().getFirst().setAppointmentCapacity(1);
        Long id = book(customer, "10:00"); var s = session(admin);
        change(id, "{\"status\":\"CONFIRMED\"}", s).andExpect(status().isOk()).andExpect(jsonPath("$.appointment.status").value("CONFIRMED"));
        change(id, "{\"status\":\"COMPLETED\"}", s).andExpect(status().isConflict());
        cancel(id, "{\"reason\":\"Bận\"}", session(customer)).andExpect(status().isConflict());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var a = appointments.findById(id).orElseThrow(); a.setAppointmentDate(date().minusDays(8));
        });
        change(id, "{\"status\":\"COMPLETED\"}", s).andExpect(status().isOk()).andExpect(jsonPath("$.appointment.statusLabel").value("Hoàn thành"));
        change(id, "{\"status\":\"REJECTED\",\"reason\":\"Không thể ghi đè\"}", s).andExpect(status().isConflict());
        assertEquals(3, history.count());
    }

    @Test void customerCancellationRecordsReasonAndReleasesCapacity() throws Exception {
        stores.getBranches().getFirst().setAppointmentCapacity(1);
        Long id = book(customer, "10:00"); var s = session(customer);
        cancel(id, "{\"reason\":\"   \"}", s).andExpect(status().isBadRequest());
        cancel(id, "{\"reason\":\"  Đổi kế hoạch  \"}", s).andExpect(status().isOk())
            .andExpect(jsonPath("$.appointment.status").value("CANCELLED")).andExpect(jsonPath("$.appointment.canCancel").value(false))
            .andExpect(jsonPath("$.history[1].reason").value("Đổi kế hoạch"));
        cancel(id, "{\"reason\":\"Hủy lại\"}", s).andExpect(status().isConflict());
        assertNotNull(book(other, "10:00")); assertEquals(3, history.count());
    }

    @Test void overduePendingCannotBeCancelledOrConfirmed() throws Exception {
        Long id = book(customer, "10:00");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> appointments.findById(id).orElseThrow().setAppointmentDate(date().minusDays(8)));
        cancel(id, "{\"reason\":\"Đã quá giờ\"}", session(customer)).andExpect(status().isConflict());
        change(id, "{\"status\":\"CONFIRMED\"}", session(admin)).andExpect(status().isBadRequest());
        assertEquals(1, history.count());
    }

    @Test void listFiltersAndPaginationUseRealRecords() throws Exception {
        Long first = book(customer, "10:00"); book(other, "11:00"); book(customer, "12:00");
        cancel(first, "{\"reason\":\"Bận\"}", session(customer)).andExpect(status().isOk());
        mvc.perform(get("/api/appointments").session(session(customer)).param("status", "PENDING"))
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/admin/appointments").session(session(admin)).param("size", "1"))
            .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/admin/appointments").session(session(admin)).param("status", "CANCELLED"))
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/appointments").session(session(customer)).param("status", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/appointments").session(session(customer)).param("size", "1000")).andExpect(status().isBadRequest());
    }

    @Test void validatesDatePrecisionShowroomOpeningHoursClosedDaysAndCapacity() {
        var branch = stores.getBranches().getFirst();
        for (String time : List.of("07:59", "20:00", "19:45", "10:00:01")) {
            var f = form(time); assertThrows(IllegalArgumentException.class, () -> service.createAppointment(f));
        }
        var past = form("10:00"); past.setAppointmentDate(LocalDate.now(AppointmentService.ZONE).minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.createAppointment(past));
        var unknown = form("10:00"); unknown.setShowroom("Cửa hàng không có");
        assertThrows(IllegalArgumentException.class, () -> service.createAppointment(unknown));
        branch.setAppointmentsEnabled(false); assertThrows(IllegalArgumentException.class, () -> service.createAppointment(form("10:00")));
        branch.setAppointmentsEnabled(true); branch.setAppointmentDays(Set.of(date().getDayOfWeek().plus(1)));
        assertThrows(IllegalArgumentException.class, () -> service.createAppointment(form("10:00")));
        branch.setAppointmentDays(null); branch.setOpeningHours("Giờ mở cửa chưa xác minh");
        assertThrows(IllegalArgumentException.class, () -> service.createAppointment(form("10:00")));
        branch.setOpeningHours("08:00–20:00"); branch.setAppointmentCapacity(0);
        assertThrows(IllegalArgumentException.class, () -> service.createAppointment(form("10:00")));
        assertEquals(0, appointments.count()); assertEquals(0, history.count());
    }

    @Test void overlappingCapacityIsEnforcedAndAdjacentIntervalsRemainBookable() {
        stores.getBranches().getFirst().setAppointmentCapacity(1);
        book(customer, "10:00");
        assertThrows(ResponseStatusException.class, () -> book(other, "10:15"));
        assertNotNull(book(other, "10:30")); assertEquals(2, appointments.count()); assertEquals(2, history.count());
    }

    @Test void capacityUsesPeakConcurrencyRatherThanTotalOverlappingRows() {
        book(customer, "10:00"); book(customer, "10:30");
        assertNotNull(book(other, "10:15"));
        assertThrows(ResponseStatusException.class, () -> book(other, "10:20"));
        assertEquals(3, appointments.count());
    }

    @Test void simultaneousBookingsCannotExceedLastPlaceEvenWithAnInitiallyEmptySchedule() throws Exception {
        stores.getBranches().getFirst().setAppointmentCapacity(1);
        var executor = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            Callable<Boolean> task = () -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                try { book(null, "10:00"); return true; }
                catch (ResponseStatusException e) { assertEquals(409, e.getStatusCode().value()); return false; }
            };
            var first = executor.submit(task); var second = executor.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, appointments.count()); assertEquals(1, management.count()); assertEquals(1, history.count());
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void confirmationAndCancellationRaceHasExactlyOneSuccessfulTransition() throws Exception {
        Long id = book(customer, "10:00"); var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var confirm = executor.submit(() -> race(start, admin, () -> service.changeStatus(id, "CONFIRMED", null, null)));
            var cancel = executor.submit(() -> race(start, customer, () -> service.cancel(id, "Bận")));
            start.countDown(); assertNotEquals(confirm.get(20, TimeUnit.SECONDS), cancel.get(20, TimeUnit.SECONDS));
            assertEquals(2, history.count());
            assertTrue(List.of("CONFIRMED", "CANCELLED").contains(appointments.findById(id).orElseThrow().getStatus()));
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void adminSupportTabRendersRealFilteredRecordsAndPostsToTheSameStatusService() throws Exception {
        Long id = book(customer, "10:00"); var s = session(admin); var token = csrf(s);
        mvc.perform(get("/admin/support").session(s).param("tab", "appointments").param("status", "PENDING"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Khách đặt lịch")))
            .andExpect(content().string(containsString("Chờ xác nhận")))
            .andExpect(content().string(containsString("/admin/support/appointments/" + id + "/status")))
            .andExpect(content().string(not(containsString("chưa có kết nối"))));
        mvc.perform(post("/admin/support/appointments/" + id + "/status").session(s)
                .param(token.getParameterName(), token.getToken()).param("status", "CONFIRMED"))
            .andExpect(redirectedUrl("/admin/support?tab=appointments"));
        assertEquals("CONFIRMED", appointments.findById(id).orElseThrow().getStatus());
        mvc.perform(get("/admin/support").session(s).param("tab", "appointments").param("status", "PENDING"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa có lịch hẹn phù hợp")));
        mvc.perform(post("/admin/support/appointments/" + id + "/status").session(session(customer)).param("status", "REJECTED"))
            .andExpect(status().isForbidden());
    }

    @Test void adminConfirmationAndCompletionReachCustomerHistoryWithoutDuplicateRecordsOrEvents() throws Exception {
        var customerSession = session(customer); var token = csrf(customerSession);
        mvc.perform(post("/datlich").session(customerSession).param(token.getParameterName(), token.getToken())
                .param("fullName", "Khách quy trình").param("phone", customer.getPhone()).param("email", customer.getEmail())
                .param("supportType", "Hỗ trợ kỹ thuật").param("showroom", "LAPZONE Kiểm thử")
                .param("appointmentDate", date().toString()).param("appointmentTime", "10:00")
                .param("description", "Cần kiểm tra nhiệt độ máy"))
            .andExpect(redirectedUrl("/datlich"));
        Long id = appointments.findAll().getFirst().getId(); var adminSession = session(admin);
        mvc.perform(get("/admin/support").session(adminSession).param("tab", "appointments").param("appointmentId", id.toString()))
            .andExpect(status().isOk()).andExpect(model().attribute("pendingAppointmentCount", 1L))
            .andExpect(content().string(containsString("Chi tiết lịch hẹn #" + id)))
            .andExpect(content().string(containsString("Cần kiểm tra nhiệt độ máy")))
            .andExpect(content().string(containsString("Lịch sử cập nhật")))
            .andExpect(content().string(containsString("/js/admin-appointments.js")));
        var adminToken = csrf(adminSession);
        mvc.perform(post("/admin/support/appointments/" + id + "/status").session(adminSession)
                .param(adminToken.getParameterName(), adminToken.getToken()).param("status", "CONFIRMED")
                .param("filterStatus", "PENDING").param("appointmentId", id.toString()))
            .andExpect(redirectedUrl("/admin/support?tab=appointments&status=PENDING&appointmentId=" + id))
            .andExpect(flash().attribute("appointmentActionSuccess", "Lịch hẹn #" + id + ": Đã xác nhận."));
        mvc.perform(get("/datlich").session(session(customer)).param("tab", "history")).andExpect(status().isOk());
        mvc.perform(get("/api/appointments/" + id).session(session(customer))).andExpect(status().isOk())
            .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.appointment.canCancel").value(false)).andExpect(jsonPath("$.history.length()").value(2));
        var beforeTime = mvc.perform(get("/admin/support").session(adminSession).param("tab", "appointments")
                .param("appointmentId", id.toString())).andExpect(model().attribute("pendingAppointmentCount", 0L)).andReturn();
        var beforeDetail = (AppointmentService.Detail) Objects.requireNonNull(beforeTime.getModelAndView()).getModel().get("appointmentDetails");
        assertTrue(beforeDetail.appointment().adminActions().isEmpty());
        adminForm(id, "CONFIRMED", "", adminSession).andExpect(flash().attribute("error", "Không thể chuyển trạng thái lịch hẹn theo yêu cầu."));
        adminForm(id, "COMPLETED", "", adminSession).andExpect(flash().attribute("error", "Chỉ được hoàn thành lịch hẹn khi đã đến thời gian hẹn."));
        assertEquals(1, appointments.count()); assertEquals(2, history.count());
        // Test-only clock progression: keep the real appointment ID, move its scheduled date in isolated H2.
        new TransactionTemplate(transactions).executeWithoutResult(tx -> appointments.findById(id).orElseThrow().setAppointmentDate(date().minusDays(8)));
        var due = mvc.perform(get("/admin/support").session(adminSession).param("tab", "appointments")
                .param("appointmentId", id.toString())).andExpect(status().isOk()).andReturn();
        var dueDetail = (AppointmentService.Detail) Objects.requireNonNull(due.getModelAndView()).getModel().get("appointmentDetails");
        assertEquals(List.of("COMPLETED"), dueDetail.appointment().adminActions());
        adminForm(id, "COMPLETED", "Đã kiểm tra và bàn giao", adminSession)
            .andExpect(flash().attribute("appointmentActionSuccess", "Lịch hẹn #" + id + ": Hoàn thành."));
        mvc.perform(get("/api/appointments").session(session(customer)).param("status", "COMPLETED"))
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/appointments/" + id).session(session(customer))).andExpect(jsonPath("$.history.length()").value(3));
        adminForm(id, "COMPLETED", "", adminSession).andExpect(flash().attributeExists("error"));
        assertEquals(1, appointments.count()); assertEquals(3, history.count());
    }

    @Test void adminRejectionRequiresReasonAndDetailsAndCustomerHistoryShowTheSameEscapedResult() throws Exception {
        Long id = book(customer, "11:00"); var s = session(admin);
        adminForm(id, "REJECTED", "   ", s).andExpect(flash().attributeExists("error"));
        assertEquals("PENDING", appointments.findById(id).orElseThrow().getStatus()); assertEquals(1, history.count());
        String reason = "<script>alert('lý do')</script> Cần đặt lại lịch";
        adminForm(id, "REJECTED", reason, s).andExpect(flash().attribute("appointmentActionSuccess", "Lịch hẹn #" + id + ": Đã từ chối."));
        mvc.perform(get("/admin/support").session(s).param("tab", "appointments").param("status", "REJECTED").param("appointmentId", id.toString()))
            .andExpect(status().isOk()).andExpect(model().attribute("pendingAppointmentCount", 0L))
            .andExpect(content().string(containsString("&lt;script&gt;")))
            .andExpect(content().string(not(containsString(reason))));
        mvc.perform(get("/api/appointments/" + id).session(session(customer))).andExpect(status().isOk())
            .andExpect(jsonPath("$.appointment.status").value("REJECTED"))
            .andExpect(jsonPath("$.appointment.statusReason").value(reason)).andExpect(jsonPath("$.history.length()").value(2));
        mvc.perform(get("/admin/support").session(s).param("tab", "messages"))
            .andExpect(status().isOk()).andExpect(model().attribute("pendingAppointmentCount", 0L));
        mvc.perform(get("/admin/support").session(session(other)).param("tab", "appointments").param("appointmentId", id.toString()))
            .andExpect(status().isForbidden());
        adminForm(id, "REJECTED", "Không được ghi lặp", s).andExpect(flash().attributeExists("error"));
        assertEquals(1, appointments.count()); assertEquals(2, history.count());
    }

    @Test void adminPendingCountAndAvailableActionsRespectOverdueAndMissingAppointments() throws Exception {
        Long id = book(customer, "10:00");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> appointments.findById(id).orElseThrow().setAppointmentDate(date().minusDays(8)));
        var result = mvc.perform(get("/admin/support").session(session(admin)).param("tab", "appointments").param("appointmentId", id.toString()))
            .andExpect(status().isOk()).andExpect(model().attribute("pendingAppointmentCount", 1L)).andReturn();
        var details = (AppointmentService.Detail) Objects.requireNonNull(result.getModelAndView()).getModel().get("appointmentDetails");
        assertEquals(List.of("REJECTED"), details.appointment().adminActions());
        mvc.perform(get("/admin/support").session(session(admin)).param("tab", "appointments").param("appointmentId", "99999999"))
            .andExpect(status().isNotFound()).andExpect(content().string(containsString("Không tìm thấy lịch hẹn")));
        auth(customer); assertThrows(org.springframework.security.access.AccessDeniedException.class, service::adminPendingCount);
    }

    @Test void adminFilteredPaginationRecoversAfterTheLastRowChangesStatus() throws Exception {
        Long oldest = null;
        for (int i = 0; i < 21; i++) {
            var f = form("10:00"); f.setShowroom(null);
            Long id = service.createAppointment(f).getId();
            if (oldest == null) oldest = id;
        }
        var s = session(admin);
        adminForm(oldest, "REJECTED", "Đã liên hệ khách hàng", s).andExpect(flash().attributeExists("appointmentActionSuccess"));
        var result = mvc.perform(get("/admin/support").session(s).param("tab", "appointments").param("status", "PENDING").param("page", "1"))
            .andExpect(status().isOk()).andExpect(model().attribute("pendingAppointmentCount", 20L)).andReturn();
        var page = (AppointmentService.AppointmentPage) Objects.requireNonNull(result.getModelAndView()).getModel().get("appointments");
        assertEquals(0, page.number()); assertEquals(20, page.content().size());
        assertEquals(21, appointments.count());
    }

    @Test void guestSubmissionStillWorksAndSessionContactDataCannotAssignOwnership() throws Exception {
        var s = new MockHttpSession(); s.setAttribute("currentUser", customer); var token = csrf(s);
        mvc.perform(post("/datlich").session(s).param(token.getParameterName(), token.getToken())
                .param("fullName", customer.getFullName()).param("phone", customer.getPhone()).param("email", customer.getEmail())
                .param("supportType", "Khác").param("appointmentDate", date().toString()).param("appointmentTime", "10:00"))
            .andExpect(redirectedUrl("/datlich"));
        assertEquals(1, appointments.count()); assertNull(management.findAll().getFirst().getCustomer());
        mvc.perform(get("/api/appointments").session(session(customer))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void bookingFormAndFreshSessionHistoryUseTheSamePersistedRecord() throws Exception {
        var bookingSession = session(customer); var token = csrf(bookingSession);
        mvc.perform(post("/datlich").session(bookingSession).param(token.getParameterName(), token.getToken())
                .param("fullName", "Khách đặt lịch").param("phone", "0900000000").param("email", customer.getEmail())
                .param("supportType", "Hỗ trợ kỹ thuật").param("showroom", "LAPZONE Kiểm thử")
                .param("appointmentDate", date().toString()).param("appointmentTime", "10:30")
                .param("description", "Cần kiểm tra máy"))
            .andExpect(redirectedUrl("/datlich"));
        Long id = appointments.findAll().getFirst().getId();
        var laterSession = session(customer);
        mvc.perform(get("/datlich").session(laterSession).param("tab", "history"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Lịch hẹn của tôi")))
            .andExpect(content().string(containsString("data-appointments-url=\"/api/appointments\"")))
            .andExpect(content().string(containsString("/js/appointments-customer.js")));
        mvc.perform(get("/api/appointments").session(laterSession).param("status", "PENDING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(id))
            .andExpect(jsonPath("$.content[0].canCancel").value(true));
        mvc.perform(get("/api/appointments/" + id).session(laterSession)).andExpect(status().isOk())
            .andExpect(jsonPath("$.appointment.description").value("Cần kiểm tra máy"))
            .andExpect(jsonPath("$.appointment.appointmentTime").value("10:30:00"))
            .andExpect(jsonPath("$.history.length()").value(1));
        cancel(id, "{\"reason\":\"Không thể đến đúng giờ\"}", laterSession).andExpect(status().isOk());
        mvc.perform(get("/api/appointments/" + id).session(session(customer))).andExpect(status().isOk())
            .andExpect(jsonPath("$.appointment.statusLabel").value("Đã hủy"))
            .andExpect(jsonPath("$.appointment.statusReason").value("Không thể đến đúng giờ"));
        mvc.perform(get("/api/appointments").session(session(customer)).param("status", "PENDING"))
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/appointments/" + id).session(session(other))).andExpect(status().isNotFound());
    }

    @Test void bookingValidationAndPublicHistoryShellDoNotExposeGuestAppointments() throws Exception {
        Long guestId = book(null, "10:00");
        mvc.perform(get("/datlich").param("tab", "history")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Lịch hẹn của tôi")))
            .andExpect(content().string(not(containsString("Khách đặt lịch"))));
        mvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/" + guestId).session(session(customer))).andExpect(status().isNotFound());
        var s = session(customer); var token = csrf(s);
        mvc.perform(post("/datlich").session(s).param(token.getParameterName(), token.getToken())
                .param("fullName", "Khách đặt lịch").param("phone", "0900000000").param("email", "email@example.test")
                .param("supportType", "Hỗ trợ kỹ thuật"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Vui lòng chọn ngày hẹn.")));
        assertEquals(1, appointments.count());
    }

    @Test void customerHistoryOrdersByCreationTimeRatherThanScheduledTime() throws Exception {
        Long first = book(customer, "12:00");
        Long second = book(customer, "09:00");
        mvc.perform(get("/api/appointments").session(session(customer))).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value(second))
            .andExpect(jsonPath("$.content[1].id").value(first));
    }

    @Test void noPreferencePendingRequiresShowroomAssignmentBeforeConfiguredConfirmation() throws Exception {
        var f = form("10:00"); f.setShowroom(null); Long id = service.createAppointment(f).getId();
        change(id, "{\"status\":\"CONFIRMED\"}", session(admin)).andExpect(status().isBadRequest());
        change(id, "{\"status\":\"CONFIRMED\",\"showroom\":\"LAPZONE Kiểm thử\"}", session(admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.appointment.showroom").value("LAPZONE Kiểm thử"));
        assertEquals(2, history.count());
    }

    @Test void savedBookingDurationRemainsEffectiveWhenConfigurationChanges() {
        var branch = stores.getBranches().getFirst(); branch.setAppointmentCapacity(1);
        book(customer, "10:00"); branch.setAppointmentDurationMinutes(15);
        assertThrows(ResponseStatusException.class, () -> book(other, "10:20"));
        assertNotNull(book(other, "10:30")); assertEquals(2, appointments.count());
    }

    @Test void auditFailureRollsBackAppointmentMetadataAndCapacityReservation() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Không thể ghi lịch sử kiểm thử"))
            .when(history).save(org.mockito.ArgumentMatchers.any(AppointmentStatusHistory.class));
        assertThrows(IllegalStateException.class, () -> book(customer, "10:00"));
        assertEquals(0, appointments.count()); assertEquals(0, management.count()); assertEquals(0, locks.count());
    }

    @Test void unknownLegacyStatusIsReadableButCannotBeRewrittenAsAnAllowedTransition() throws Exception {
        var legacy = new Appointment(); legacy.setFullName("Lịch nhập cũ"); legacy.setPhone("0900000000");
        legacy.setEmail("legacy@test.local"); legacy.setSupportType("Khác"); legacy.setAppointmentDate(date());
        legacy.setAppointmentTime(LocalTime.of(10, 0)); legacy.setStatus("LEGACY"); appointments.saveAndFlush(legacy);
        mvc.perform(get("/admin/appointments/" + legacy.getId()).session(session(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.appointment.status").value("LEGACY")).andExpect(jsonPath("$.appointment.adminActions").isEmpty());
        change(legacy.getId(), "{\"status\":\"REJECTED\",\"reason\":\"Không tự ý đổi dữ liệu cũ\"}", session(admin)).andExpect(status().isConflict());
        assertFalse(management.existsById(legacy.getId()));
    }
    boolean race(CountDownLatch start, User user, Runnable mutation) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS)); auth(user);
        try { mutation.run(); return true; }
        catch (ResponseStatusException e) { assertEquals(409, e.getStatusCode().value()); return false; }
        finally { SecurityContextHolder.clearContext(); }
    }
}
