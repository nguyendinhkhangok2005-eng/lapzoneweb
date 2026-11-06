package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromotionService {

    private static final Map<Long, Integer> DEAL_DISCOUNTS = Map.ofEntries(
            Map.entry(1L, 10), Map.entry(2L, 15), Map.entry(3L, 8), Map.entry(4L, 12),
            Map.entry(5L, 20), Map.entry(6L, 5), Map.entry(7L, 10), Map.entry(8L, 15),
            Map.entry(101L, 10), Map.entry(102L, 15), Map.entry(103L, 8), Map.entry(104L, 12),
            Map.entry(105L, 20), Map.entry(106L, 5), Map.entry(107L, 10), Map.entry(108L, 15));

    @Autowired
    private ProductRepository productRepository;

    public List<Product> getDealProducts() {
        List<Long> productIds = DEAL_DISCOUNTS.keySet().stream().sorted().toList();
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .filter(product -> product.getId() != null)
                .collect(Collectors.toMap(Product::getId, product -> product));

        List<Product> deals = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productsById.get(productId);
            if (hasDisplayableDeal(product)) {
                deals.add(product);
            }
        }
        return deals;
    }

    public static int getDiscountPercent(Product product) {
        if (product == null || product.getId() == null) {
            return 0;
        }
        return DEAL_DISCOUNTS.getOrDefault(product.getId(), 0);
    }

    public static Double getDiscountedPrice(Product product) {
        if (product == null || product.getPrice() == null) {
            return null;
        }
        int discountPercent = getDiscountPercent(product);
        if (discountPercent == 0) {
            return product.getPrice();
        }
        return BigDecimal.valueOf(product.getPrice())
                .multiply(BigDecimal.valueOf(100 - discountPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private boolean hasDisplayableDeal(Product product) {
        return product != null
                && product.getName() != null && !product.getName().isBlank()
                && product.getImage() != null && !product.getImage().isBlank()
                && product.getPrice() != null && product.getPrice() > 0
                && product.getStock() != null && product.getStock() > 0;
    }
}
