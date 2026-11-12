package com.lapzone.lapzoneweb.controller;

import org.springframework.stereotype.Component;
import com.lapzone.lapzoneweb.model.service.OrderStatusPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component("orderViewFormat")
public class OrderViewFormat {
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", VIETNAMESE);

    public String statusLabel(String value) {
        if (value == null || value.isBlank()) return "Chưa có trạng thái";
        return switch (value) {
            case OrderStatusPolicy.PENDING -> "Chờ xác nhận đơn hàng";
            case OrderStatusPolicy.WAITING_PAYMENT -> "Chờ thanh toán";
            case OrderStatusPolicy.PAID -> "Đã thanh toán";
            case OrderStatusPolicy.PACKING -> "Đang đóng gói";
            case OrderStatusPolicy.SHIPPING, "ĐANG GIAO" -> "Đang giao hàng";
            case OrderStatusPolicy.DELIVERED -> "Hoàn thành";
            case OrderStatusPolicy.CANCELLED -> "Đã hủy";
            case OrderStatusPolicy.PAYMENT_FAILED -> "Thanh toán thất bại";
            default -> value;
        };
    }

    public record CustomerStatus(String paymentCode, String paymentLabel, String fulfillmentCode, String fulfillmentLabel) {}

    public CustomerStatus customerStatus(String storedStatus) {
        // Read-only presentation: a payment method or fulfillment stage never proves payment success.
        // Only the explicit, persisted PAID confirmation produces a paid badge.
        if (OrderStatusPolicy.PAID.equals(storedStatus))
            return new CustomerStatus(OrderStatusPolicy.PAID, statusLabel(storedStatus),
                    OrderStatusPolicy.PENDING, statusLabel(OrderStatusPolicy.PENDING));
        if (OrderStatusPolicy.WAITING_PAYMENT.equals(storedStatus) || OrderStatusPolicy.PAYMENT_FAILED.equals(storedStatus))
            return new CustomerStatus(storedStatus, statusLabel(storedStatus), null, null);
        return new CustomerStatus(null, null, storedStatus, statusLabel(storedStatus));
    }

    public String paymentMethod(String value) {
        if (value == null || value.isBlank()) return "Chưa ghi nhận";
        return switch (value) {
            case "COD" -> "Thanh toán khi nhận hàng";
            case "VNPAY" -> "VNPay";
            case "MOMO" -> "MoMo";
            default -> value;
        };
    }

    public String money(Number amount) {
        if (amount == null) return "Chưa có giá";
        BigDecimal value = amount instanceof BigDecimal decimal ? decimal : BigDecimal.valueOf(amount.doubleValue());
        NumberFormat format = NumberFormat.getNumberInstance(VIETNAMESE);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value) + " ₫";
    }

    public String date(Instant instant) {
        return instant == null ? "Chưa có ngày đặt" : DATE_TIME.format(LocalDateTime.ofInstant(instant, VIETNAM));
    }

    public String date(LocalDateTime dateTime) {
        return dateTime == null ? "Chưa có thời gian" : DATE_TIME.format(dateTime);
    }

    public String imageUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;
        String path = storedPath.trim();
        if (path.contains("..") || path.startsWith("//") || path.indexOf('\\') >= 0
                || path.chars().anyMatch(Character::isISOControl)) return null;
        if (path.startsWith("https://") || path.startsWith("http://")) return path;
        if (path.indexOf(':') >= 0) return null;
        if (path.startsWith("/")) return path;
        if (path.startsWith("assets/") || path.startsWith("images/") || path.startsWith("uploads/")) return "/" + path;
        return "/assets/" + path;
    }

    public String caseType(String value) {
        return switch (value) {
            case "RETURN" -> "Trả hàng";
            case "REFUND" -> "Hoàn tiền";
            default -> "Hồ sơ hỗ trợ";
        };
    }

    public String caseStatus(String value) {
        return switch (value) {
            case "REQUESTED" -> "Chờ xử lý";
            case "APPROVED" -> "Đã chấp nhận";
            case "REJECTED" -> "Từ chối";
            case "COMPLETED" -> "Hoàn tất";
            case "CANCELLED" -> "Đã hủy";
            default -> "Chưa có trạng thái";
        };
    }
}
