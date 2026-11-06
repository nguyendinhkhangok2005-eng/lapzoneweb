package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.service.PromotionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PromotionServiceTest {

    @Test
    void appliesConfiguredDiscountToOriginalProductPrice() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(25_000_000.0);

        assertEquals(10, PromotionService.getDiscountPercent(product));
        assertEquals(22_500_000.0, PromotionService.getDiscountedPrice(product));
    }

    @Test
    void appliesEachDiscountAsAStablePercentageOfTheOriginalPrice() {
        Product product = new Product();
        product.setId(2L);
        product.setPrice(25_000_000.0);

        assertEquals(15, PromotionService.getDiscountPercent(product));
        assertEquals(21_250_000.0, PromotionService.getDiscountedPrice(product));
    }

    @Test
    void returnsOriginalPriceForProductsWithoutPromotion() {
        Product product = new Product();
        product.setId(999L);
        product.setPrice(25_000_000.0);

        assertEquals(0, PromotionService.getDiscountPercent(product));
        assertEquals(25_000_000.0, PromotionService.getDiscountedPrice(product));
    }

    @Test
    void rejectsNoProductAsNotPromotional() {
        assertEquals(0, PromotionService.getDiscountPercent(null));
        assertNull(PromotionService.getDiscountedPrice(null));
    }
}
