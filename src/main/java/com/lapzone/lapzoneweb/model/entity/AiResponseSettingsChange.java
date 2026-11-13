package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_response_settings_changes", indexes = @Index(name = "idx_ai_setting_change_time", columnList = "changed_at,id"))
public class AiResponseSettingsChange {
    public enum Action {
        UPDATE("Cập nhật"), RESET("Khôi phục mặc định");
        private final String label;
        Action(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private Action action;
    @Column(name = "before_value", nullable = false, length = 500)
    private String beforeValue;
    @Column(name = "after_value", nullable = false, length = 500)
    private String afterValue;
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt = Instant.now();

    protected AiResponseSettingsChange() {}
    public AiResponseSettingsChange(Long adminId, Action action, String beforeValue, String afterValue) {
        this.adminId = adminId; this.action = action; this.beforeValue = beforeValue; this.afterValue = afterValue;
    }
    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public Action getAction() { return action; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public Instant getChangedAt() { return changedAt; }
}
