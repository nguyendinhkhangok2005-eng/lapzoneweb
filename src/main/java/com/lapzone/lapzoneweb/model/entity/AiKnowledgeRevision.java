package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;

@Entity
@Table(name = "ai_knowledge_revisions", uniqueConstraints = @UniqueConstraint(name = "uk_ai_knowledge_version", columnNames = {"entry_id", "version_no"}))
public class AiKnowledgeRevision {
    public enum Category {
        FAQ("Câu hỏi thường gặp"), WARRANTY("Chính sách bảo hành"), RETURNS("Chính sách đổi trả và hoàn tiền"),
        PAYMENT_SHIPPING("Thanh toán và giao hàng"), STORE("Thông tin cửa hàng"), BUYING_GUIDE("Hướng dẫn chọn mua laptop");
        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entry_id", nullable = false)
    private AiKnowledgeEntry entry;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(nullable = false, length = 300)
    private String keywords;
    @Column(name = "search_text", nullable = false, columnDefinition = "TEXT")
    private String searchText;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private AiKnowledgeEntry.Action action;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "editor_id") @OnDelete(action = OnDeleteAction.SET_NULL)
    private User editor;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AiKnowledgeRevision() {}
    public AiKnowledgeRevision(AiKnowledgeEntry entry, String title, String content, String keywords, String searchText,
                               Category category, AiKnowledgeEntry.Action action, User editor) {
        this.entry = entry; this.versionNo = entry.getCurrentVersion(); this.title = title; this.content = content;
        this.keywords = keywords; this.searchText = searchText; this.category = category; this.action = action; this.editor = editor;
    }
    public Long getId() { return id; }
    public AiKnowledgeEntry getEntry() { return entry; }
    public int getVersionNo() { return versionNo; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getKeywords() { return keywords; }
    public String getSearchText() { return searchText; }
    public Category getCategory() { return category; }
    public AiKnowledgeEntry.Action getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
}
