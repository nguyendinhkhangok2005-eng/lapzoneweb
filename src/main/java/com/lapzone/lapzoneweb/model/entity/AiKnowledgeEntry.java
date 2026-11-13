package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_knowledge_entries", indexes = @Index(name = "idx_ai_knowledge_state", columnList = "state,updated_at"))
public class AiKnowledgeEntry {
    public enum State {
        DRAFT("Bản nháp"), PUBLISHED("Đã xuất bản"), ARCHIVED("Đã lưu trữ");
        private final String label;
        State(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    public enum Action {
        CREATE("Tạo mới"), EDIT("Chỉnh sửa"), PUBLISH("Xuất bản"), UNPUBLISH("Ngừng xuất bản"), ARCHIVE("Lưu trữ");
        private final String label;
        Action(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private State state = State.DRAFT;
    @Column(name = "current_version", nullable = false)
    private int currentVersion;
    @Column(name = "published_version")
    private Integer publishedVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = createdAt;

    public AiKnowledgeEntry() {}
    public void advance(Action action) {
        currentVersion++; updatedAt = Instant.now();
        switch (action) {
            case PUBLISH -> { state = State.PUBLISHED; publishedVersion = currentVersion; }
            case UNPUBLISH -> { state = State.DRAFT; publishedVersion = null; }
            case ARCHIVE -> { state = State.ARCHIVED; publishedVersion = null; }
            default -> { }
        }
    }
    public Long getId() { return id; }
    public State getState() { return state; }
    public int getCurrentVersion() { return currentVersion; }
    public Integer getPublishedVersion() { return publishedVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
}
