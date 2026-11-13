package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_response_settings")
public class AiResponseSettings {
    public enum Style {
        FRIENDLY("Thân thiện"), PROFESSIONAL("Chuyên nghiệp"), CONCISE("Ngắn gọn");
        private final String label;
        Style(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    public enum Length {
        SHORT("Ngắn"), MEDIUM("Trung bình"), DETAILED("Chi tiết");
        private final String label;
        Length(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id
    private Long id = 1L;
    @Version
    private Long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Style style = Style.FRIENDLY;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private Length responseLength = Length.MEDIUM;
    @Column(nullable = false)
    private int maxRecommendations = 3;
    @Column(nullable = false)
    private boolean highlightPromotions;
    @Column(nullable = false)
    private boolean suggestRelatedProducts;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @Column(nullable = false)
    private Long updatedById;

    public AiResponseSettings() {}
    public void apply(Style style, Length responseLength, int maxRecommendations,
                      boolean highlightPromotions, boolean suggestRelatedProducts, Long adminId) {
        this.style = style; this.responseLength = responseLength; this.maxRecommendations = maxRecommendations;
        this.highlightPromotions = highlightPromotions; this.suggestRelatedProducts = suggestRelatedProducts;
        this.updatedAt = Instant.now(); this.updatedById = adminId;
    }
    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public Style getStyle() { return style; }
    public Length getResponseLength() { return responseLength; }
    public int getMaxRecommendations() { return maxRecommendations; }
    public boolean isHighlightPromotions() { return highlightPromotions; }
    public boolean isSuggestRelatedProducts() { return suggestRelatedProducts; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getUpdatedById() { return updatedById; }
}
