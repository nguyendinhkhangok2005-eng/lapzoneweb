package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.Order;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Existing database values remain unchanged; categories are separate from statuses. */
public final class OrderStatusPolicy {
    private OrderStatusPolicy() {}
    public static final String WAITING_PAYMENT = "CHỜ THANH TOÁN";
    public static final String PENDING = "CHỜ XÁC NHẬN";
    public static final String PAID = "ĐÃ THANH TOÁN";
    public static final String PACKING = "ĐANG ĐÓNG GÓI";
    public static final String SHIPPING = "ĐANG GIAO HÀNG";
    public static final String DELIVERED = "HOÀN THÀNH";
    public static final String CANCELLED = "ĐÃ HỦY";
    public static final String PAYMENT_FAILED = "THANH TOÁN THẤT BẠI";
    public static final List<String> ALL = List.of(WAITING_PAYMENT, PENDING, PAID, PACKING, SHIPPING, DELIVERED, CANCELLED, PAYMENT_FAILED);
    // Customer grouping only; this does not grant any fulfillment/payment transition.
    public static final List<String> PENDING_STATUSES = List.of(PENDING, WAITING_PAYMENT, PAID);
    public static final List<String> SHIPPING_STATUSES = List.of(PACKING, SHIPPING, "ĐANG GIAO");
    public static final List<String> DELIVERED_STATUSES = List.of(DELIVERED);
    private static final Map<String, List<String>> NEXT = Map.of(
            WAITING_PAYMENT, List.of(PAID, PAYMENT_FAILED, CANCELLED),
            PENDING, List.of(PACKING, CANCELLED),
            PAID, List.of(PACKING, CANCELLED),
            PACKING, List.of(SHIPPING, CANCELLED),
            SHIPPING, List.of(DELIVERED),
            "ĐANG GIAO", List.of(DELIVERED));

    public static List<String> allowed(Order order) { return NEXT.getOrDefault(order.getStatus() == null ? "" : order.getStatus(), List.of()); }

    public static void validate(Order order, String target) {
        if (target == null || !ALL.contains(target)) throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ.");
        if (Objects.equals(order.getStatus(), target)) return; // Safe retry; no second side effects.
        if (!allowed(order).contains(target))
            throw new IllegalArgumentException("Không thể chuyển từ trạng thái hiện tại sang trạng thái đã chọn.");
    }
}
