package com.lapzone.lapzoneweb.model.dto;

public record ChatMessageDTO(String role, String text, String conversationId, java.time.Instant createdAt) {
    public ChatMessageDTO(String role, String text) {
        this(role, text, null, null);
    }
}
