package com.lapzone.lapzoneweb.model.dto;

import java.util.Map;

/** All display facts are supplied by the catalog, never by the language model. */
public record AiProductDTO(Long id, String name, Double price, String imageUrl,
                           Map<String, String> specifications, Integer stock,
                           boolean available, String detailUrl, int promotionPercent) {
    public AiProductDTO(Long id, String name, Double price, String imageUrl,
                        Map<String, String> specifications, Integer stock,
                        boolean available, String detailUrl) {
        this(id, name, price, imageUrl, specifications, stock, available, detailUrl, 0);
    }
}
