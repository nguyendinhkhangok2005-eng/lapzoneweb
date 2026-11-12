package com.lapzone.lapzoneweb.model.service;
import java.util.Locale;
public enum CustomerOrderCategory {
    ALL("Tất cả"), PENDING("Chờ xác nhận"), SHIPPING("Chờ vận chuyển"), REVIEW("Cần đánh giá"), RETURNS("Trả hàng"),
    CANCELLED("Đã hủy"), COMPLETED("Hoàn thành");
    private final String label;
    CustomerOrderCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
    public static CustomerOrderCategory parse(String value) {
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Nhóm đơn hàng không hợp lệ."); }
    }
}
