package com.lapzone.lapzoneweb.model.entity;

/** Values are stored in the existing appointments.status VARCHAR column. */
public enum AppointmentStatus {
    PENDING("Chờ xác nhận"), CONFIRMED("Đã xác nhận"), COMPLETED("Hoàn thành"),
    REJECTED("Đã từ chối"), CANCELLED("Đã hủy");

    private final String label;
    AppointmentStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    public static AppointmentStatus parse(String value) {
        try { return valueOf(value == null ? "" : value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Trạng thái lịch hẹn không hợp lệ."); }
    }
    public static String labelFor(String value) {
        try { return parse(value).getLabel(); }
        catch (IllegalArgumentException e) { return "Trạng thái cũ chưa xác định"; }
    }
}
