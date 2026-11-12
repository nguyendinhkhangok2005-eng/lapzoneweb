package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import com.lapzone.lapzoneweb.model.service.CustomerOrderService;
import com.lapzone.lapzoneweb.model.service.OrderStatusPolicy;
import com.lapzone.lapzoneweb.model.service.CustomerOrderCategory;
import com.lapzone.lapzoneweb.controller.OrderViewFormat;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in diagnostics: existing MySQL records only, no fixtures, schema changes or updates. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false",
        "spring.datasource.hikari.read-only=true",
        "spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY"})
@AutoConfigureMockMvc
@Transactional(readOnly = true)
@EnabledIfSystemProperty(named = "lapzone.test.mysql-read-only", matches = "true")
class CustomerOrderMySqlReadOnlyTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired CustomerOrderService orders;
    @Autowired MockMvc mvc;
    @Autowired OrderViewFormat display;
    @Value("${lapzone.test.customer-ids:}") String customerIds;
    @Value("${lapzone.test.status-order-ids:}") String statusOrderIds;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    List<Long> selectedUsers() {
        assertFalse(customerIds.isBlank(), "Supply actual verified account IDs with -Dlapzone.test.customer-ids.");
        return Arrays.stream(customerIds.split(",")).map(value -> Long.valueOf(value.trim())).toList();
    }

    MockHttpSession authenticate(Long userId) {
        User user = users.findById(userId).orElseThrow();
        var principal = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
        return session;
    }

    @Test void allMatchesActualOwnerRowsAcrossServiceApiAndCustomerPage() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT @@transaction_read_only", Integer.class));
        for (Long userId : selectedUsers()) {
            List<Long> expected = jdbc.queryForList(
                    "SELECT id FROM orders WHERE user_id=? ORDER BY order_date DESC,id DESC", Long.class, userId);
            var session = authenticate(userId);
            List<Long> returned = new ArrayList<>();
            var first = orders.list("ALL", 0, 50);
            returned.addAll(first.content().stream().map(order -> order.id()).toList());
            for (int page = 1; page < first.totalPages(); page++)
                returned.addAll(orders.list("ALL", page, 50).content().stream().map(order -> order.id()).toList());
            assertEquals(expected, returned);
            assertEquals(expected.size(), first.totalElements());
            var result = mvc.perform(get("/store/orders").param("category", "ALL").param("userId", "-1").session(session))
                    .andExpect(status().isOk()).andReturn();
            var page = (CustomerOrderService.OrderPage) result.getModelAndView().getModel().get("orderPage");
            assertEquals(expected.stream().limit(20).toList(), page.content().stream().map(order -> order.id()).toList());
            var api = mvc.perform(get("/api/orders").param("category", "ALL").session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(expected.size()));
            for (int index = 0; index < Math.min(expected.size(), 20); index++)
                api.andExpect(jsonPath("$.content[" + index + "].id").value(expected.get(index).intValue()));
            System.out.println("READ_ONLY_OWNER_CHECK userId=" + userId + " databaseIds=" + expected
                    + " serviceIds=" + returned + " pageIds=" + page.content().stream().map(order -> order.id()).toList());
        }
    }

    @Test void sameNamesDoNotGrantAccessToGuestOrOtherAccountsOrders() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT @@transaction_read_only", Integer.class));
        for (Long userId : selectedUsers()) {
            var session = authenticate(userId);
            List<Long> excluded = jdbc.queryForList(
                    "SELECT id FROM orders WHERE user_id IS NULL OR user_id<>? ORDER BY id LIMIT 20", Long.class, userId);
            for (Long id : excluded) {
                mvc.perform(get("/store/orders/" + id).session(session)).andExpect(status().isNotFound());
                mvc.perform(get("/api/orders/" + id).session(session)).andExpect(status().isNotFound());
            }
            System.out.println("READ_ONLY_ACCESS_CHECK userId=" + userId + " deniedOrderIds=" + excluded);
        }
    }

    @Test void realOrderStatusBadgesRespectStoredConfirmationAndOwnership() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(statusOrderIds.isBlank(), "Supply actual order IDs to trace.");
        assertEquals(1, jdbc.queryForObject("SELECT @@transaction_read_only", Integer.class));
        var paymentTables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() "
                + "AND (table_name LIKE '%payment%' OR table_name LIKE '%transaction%')", String.class);
        System.out.println("READ_ONLY_PAYMENT_TABLES " + paymentTables);
        for (String rawId : statusOrderIds.split(",")) {
            Long id = Long.valueOf(rawId.trim());
            var row = jdbc.queryForMap("SELECT user_id,status,payment_method FROM orders WHERE id=?", id);
            Long owner = row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue();
            String status = (String) row.get("status");
            var state = display.customerStatus(status);
            if (owner != null && selectedUsers().contains(owner)) {
                var session = authenticate(owner);
                var detail = mvc.perform(get("/store/orders/" + id).session(session)).andExpect(status().isOk());
                if (state.paymentLabel() != null)
                    detail.andExpect(content().string(containsString("aria-label=\"Thanh toán: " + state.paymentLabel() + "\"")));
                if (state.fulfillmentLabel() != null)
                    detail.andExpect(content().string(containsString("aria-label=\"Giao nhận: " + state.fulfillmentLabel() + "\"")));
                if (OrderStatusPolicy.PENDING_STATUSES.contains(status)) {
                    authenticate(owner);
                    assertTrue(orders.list("PENDING", 0, 50).content().stream().anyMatch(order -> order.id().equals(id)));
                    mvc.perform(get("/store/orders").param("category", "PENDING").session(session)).andExpect(status().isOk())
                            .andExpect(content().string(containsString("Đơn hàng #" + id)));
                }
            } else {
                for (Long userId : selectedUsers())
                    mvc.perform(get("/store/orders/" + id).session(authenticate(userId))).andExpect(status().isNotFound());
            }
            assertEquals(status, jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
            System.out.println("READ_ONLY_STATUS_TRACE orderId=" + id + " ownerId=" + owner + " method=" + row.get("payment_method")
                    + " rawStatus=" + status + " payment=" + state.paymentLabel() + " fulfillment=" + state.fulfillmentLabel());
        }
    }

    @Test void everyCategoryListAndCountMatchesDistinctActualOwnedOrders() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT @@transaction_read_only", Integer.class));
        for (Long userId : selectedUsers()) {
            var rows = jdbc.queryForList("""
                SELECT o.id,o.status,o.payment_method,
                    (SELECT COUNT(*) FROM order_details d WHERE d.order_id=o.id AND d.quantity>0
                        AND NOT EXISTS (SELECT 1 FROM order_item_reviews r WHERE r.order_detail_id=d.id)) AS unreviewed,
                    (SELECT COUNT(*) FROM order_return_cases c WHERE c.order_id=o.id) AS return_cases
                FROM orders o WHERE o.user_id=? ORDER BY o.order_date DESC,o.id DESC
                """, userId);
            Map<String, Set<Long>> expected = new LinkedHashMap<>();
            for (var group : CustomerOrderCategory.values()) expected.put(group.name(), new LinkedHashSet<>());
            for (var row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                String status = row.get("status") == null ? "" : row.get("status").toString();
                boolean unreviewed = ((Number) row.get("unreviewed")).longValue() > 0;
                expected.get("ALL").add(id);
                if (OrderStatusPolicy.PENDING_STATUSES.contains(status)) expected.get("PENDING").add(id);
                if (OrderStatusPolicy.SHIPPING_STATUSES.contains(status)) expected.get("SHIPPING").add(id);
                if (OrderStatusPolicy.DELIVERED_STATUSES.contains(status)) expected.get(unreviewed ? "REVIEW" : "COMPLETED").add(id);
                if (OrderStatusPolicy.CANCELLED.equals(status)) expected.get("CANCELLED").add(id);
                if (((Number) row.get("return_cases")).longValue() > 0) expected.get("RETURNS").add(id);
            }
            authenticate(userId);
            Map<String, Long> expectedCounts = new LinkedHashMap<>();
            expected.forEach((key, ids) -> expectedCounts.put(key, (long) ids.size()));
            assertEquals(expectedCounts, orders.categoryCounts());
            for (var group : CustomerOrderCategory.values()) {
                var first = orders.list(group.name(), 0, 2);
                List<Long> actual = new ArrayList<>(first.content().stream().map(order -> order.id()).toList());
                for (int page = 1; page < first.totalPages(); page++)
                    actual.addAll(orders.list(group.name(), page, 2).content().stream().map(order -> order.id()).toList());
                assertEquals(new ArrayList<>(expected.get(group.name())), actual, group.name());
                assertEquals(actual.size(), new LinkedHashSet<>(actual).size(), "Duplicate order IDs");
                assertEquals(expectedCounts.get(group.name()), first.totalElements());
                assertEquals(expectedCounts, first.categoryCounts());
                for (var order : first.content())
                    assertTrue(order.categories().stream().anyMatch(category -> category.key().equals(group.name())));
            }
            var session = authenticate(userId);
            var api = mvc.perform(get("/api/orders/categories").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoriesMayOverlap").value(true));
            for (var group : CustomerOrderCategory.values())
                api.andExpect(jsonPath("$.counts." + group.name()).value(expectedCounts.get(group.name()).intValue()));
            var page = mvc.perform(get("/store/orders").param("category", "ALL").session(session)).andExpect(status().isOk());
            for (var group : CustomerOrderCategory.values())
                page.andExpect(content().string(containsString("aria-label=\"" + group.getLabel() + ": "
                        + expectedCounts.get(group.name()) + " đơn hàng\"")));
            System.out.println("READ_ONLY_CATEGORY_CHECK userId=" + userId + " counts=" + expectedCounts
                    + " orderIds=" + expected + " missingOrderIds=[] rows=" + rows);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void refreshingCustomerPagesPreservesOwnedIdsAndCategoryCounts() throws Exception {
        assertEquals(1, jdbc.queryForObject("SELECT @@transaction_read_only", Integer.class));
        for (Long userId : selectedUsers()) {
            var session = authenticate(userId);
            List<Long> expected = jdbc.queryForList(
                    "SELECT id FROM orders WHERE user_id=? ORDER BY order_date DESC,id DESC", Long.class, userId);
            Map<String, Long> initialCounts = null;
            for (var group : CustomerOrderCategory.values()) {
                String path = "/store/orders?category=" + group.name();
                var first = mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
                var refreshed = mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
                var firstPage = (CustomerOrderService.OrderPage) first.getModelAndView().getModel().get("orderPage");
                var refreshedPage = (CustomerOrderService.OrderPage) refreshed.getModelAndView().getModel().get("orderPage");
                List<Long> firstIds = firstPage.content().stream().map(order -> order.id()).toList();
                List<Long> refreshedIds = refreshedPage.content().stream().map(order -> order.id()).toList();
                assertEquals(firstIds, refreshedIds, group.name());
                assertEquals(firstPage.totalElements(), refreshedPage.totalElements(), group.name());
                assertEquals(firstPage.categoryCounts(), refreshedPage.categoryCounts(), group.name());
                assertEquals(firstPage.totalElements(), firstPage.categoryCounts().get(group.name()), group.name());
                for (var category : CustomerOrderCategory.values()) {
                    String label = "aria-label=\"" + category.getLabel() + ": "
                            + firstPage.categoryCounts().get(category.name()) + " đơn hàng\"";
                    assertTrue(first.getResponse().getContentAsString().contains(label), label);
                    assertTrue(refreshed.getResponse().getContentAsString().contains(label), label);
                }
                if (group == CustomerOrderCategory.ALL) {
                    assertEquals(expected, firstIds);
                    initialCounts = firstPage.categoryCounts();
                }
                assertEquals(initialCounts, firstPage.categoryCounts(), group.name());
            }
            System.out.println("READ_ONLY_REFRESH_CHECK userId=" + userId + " ownedIds=" + expected
                    + " stableCounts=" + initialCounts);
        }
    }
}
