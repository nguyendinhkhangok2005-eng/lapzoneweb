package com.lapzone.lapzoneweb.model.dto;

public record ChatMessageDTO(String role, String text, String conversationId, java.time.Instant createdAt,
                             java.util.List<AiProductDTO> products, Long id, boolean supportOffered) {
    public ChatMessageDTO(String role, String text, String conversationId, java.time.Instant createdAt,
                          java.util.List<AiProductDTO> products, Long id) {
        this(role, text, conversationId, createdAt, products, id, false);
    }
    public ChatMessageDTO(String role, String text, String conversationId, java.time.Instant createdAt,
                          java.util.List<AiProductDTO> products) {
        this(role, text, conversationId, createdAt, products, null);
    }
    public ChatMessageDTO(String role, String text, String conversationId, java.time.Instant createdAt) {
        this(role, text, conversationId, createdAt, java.util.List.of());
    }
    public ChatMessageDTO(String role, String text) {
        this(role, text, null, null);
    }
}
