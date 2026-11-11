package com.lapzone.lapzoneweb.model.dto;

public record ChatRequestDTO(String message, String conversationId) {
    public ChatRequestDTO(String message) {
        this(message, null);
    }
}
