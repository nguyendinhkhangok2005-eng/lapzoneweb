package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;

@Entity
@Table(name = "ai_inspection_runs", uniqueConstraints = @UniqueConstraint(name = "uk_ai_inspection_token", columnNames = "request_token"),
    indexes = {@Index(name = "idx_ai_inspection_admin_started", columnList = "admin_id,started_at"),
        @Index(name = "idx_ai_inspection_started", columnList = "started_at,id")})
public class AiInspectionRun {
    public enum State {
        RUNNING("Đang chạy hoặc bị gián đoạn"), COMPLETED("Đã hoàn tất"), ERROR("Không hoàn tất");
        private final String label;
        State(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "admin_id") @OnDelete(action = OnDeleteAction.SET_NULL)
    private User admin;
    @Column(name = "request_token", nullable = false, length = 36, updatable = false)
    private String requestToken;
    @Column(nullable = false, length = 500, updatable = false)
    private String question;
    @Column(nullable = false, length = 20, updatable = false)
    private String preset;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private State state = State.RUNNING;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;
    @Column(length = 300)
    private String error;
    protected AiInspectionRun() {}
    public AiInspectionRun(User admin, String token, String question, String preset, Instant now) {
        this.admin = admin; this.requestToken = token; this.question = question; this.preset = preset; this.startedAt = now;
    }
    public void finish(String json, Instant now) { if (state == State.RUNNING) { resultJson = json; finishedAt = now; state = State.COMPLETED; } }
    public void fail(Instant now) { if (state == State.RUNNING) { finishedAt = now; state = State.ERROR; error = "Không hoàn tất bài kiểm tra. Vui lòng kiểm tra cấu hình hoặc thử lại sau."; } }
    public Long getId() { return id; }
    public User getAdmin() { return admin; }
    public String getQuestion() { return question; }
    public String getPreset() { return preset; }
    public State getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getResultJson() { return resultJson; }
    public String getError() { return error; }
}
