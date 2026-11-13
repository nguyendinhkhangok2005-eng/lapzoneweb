package com.lapzone.lapzoneweb.model.dto;

import java.util.List;

public record AiReplyDTO(String reply, List<AiProductDTO> products, Long messageId, boolean supportOffered) {
    public AiReplyDTO(String reply, List<AiProductDTO> products, Long messageId) { this(reply, products, messageId, false); }
    public AiReplyDTO(String reply, List<AiProductDTO> products) { this(reply, products, null); }
    public AiReplyDTO(String reply) { this(reply, List.of()); }
}
