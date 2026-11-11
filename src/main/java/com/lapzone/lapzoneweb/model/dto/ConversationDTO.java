package com.lapzone.lapzoneweb.model.dto;

import java.time.Instant;
import java.util.List;

public record ConversationDTO(String id, String title, Instant createdAt, Instant updatedAt) {
    public record Detail(ConversationDTO conversation, List<ChatMessageDTO> messages) {}
    public record TitleRequest(String title) {}
}
