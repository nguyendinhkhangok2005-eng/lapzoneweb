package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.entity.Voucher;
import com.lapzone.lapzoneweb.model.entity.VoucherRedemption;
import com.lapzone.lapzoneweb.model.repository.CategoryRepository;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.repository.VoucherRedemptionRepository;
import com.lapzone.lapzoneweb.model.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VoucherCatalogService {
    private final VoucherRepository vouchers;
    private final VoucherRedemptionRepository redemptions;
    private final ProductRepository products;
    private final CategoryRepository categories;

    public VoucherCatalogService(VoucherRepository vouchers, VoucherRedemptionRepository redemptions,
                                 ProductRepository products, CategoryRepository categories) {
        this.vouchers = vouchers;
        this.redemptions = redemptions;
        this.products = products;
        this.categories = categories;
    }

    public record Offer(String code, int discountPercent, LocalDateTime endsAt,
                        BigDecimal minimumOrderValue, int availableQuantity,
                        int perUserLimit, List<String> productNames, List<String> categoryNames,
                        boolean restricted) {}

    @Transactional(readOnly = true)
    public List<Offer> availableFor(User user) {
        LocalDateTime now = LocalDateTime.now(VoucherService.ZONE);
        Long userId = user == null ? null : user.getId();
        List<Voucher> eligible = new ArrayList<>();
        for (Voucher voucher : vouchers.findByActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAtAsc(now, now)) {
            if (voucher.getAvailableQuantity() <= 0) continue;
            Set<Long> allowedUsers = voucher.getUserIds();
            if (!allowedUsers.isEmpty() && (userId == null || !allowedUsers.contains(userId))) continue;
            if (userId != null && redemptions.countByVoucherIdAndUserIdAndStateNot(
                    voucher.getId(), userId, VoucherRedemption.State.RELEASED) >= voucher.getPerUserLimit()) continue;
            eligible.add(voucher);
        }

        Set<Long> productIds = eligible.stream().flatMap(v -> v.getProductIds().stream()).collect(Collectors.toSet());
        Set<Long> categoryIds = eligible.stream().flatMap(v -> v.getCategoryIds().stream()).collect(Collectors.toSet());
        Map<Long, String> productNames = products.findAllById(productIds).stream()
                .filter(product -> product.getName() != null)
                .collect(Collectors.toMap(product -> product.getId(), product -> product.getName()));
        Map<Long, String> categoryNames = categories.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(category -> category.getId(), category -> category.getName()));

        return eligible.stream().map(v -> new Offer(v.getCode(), v.getDiscountPercent(), v.getEndsAt(),
                v.getMinimumOrderValue(), v.getAvailableQuantity(), v.getPerUserLimit(),
                names(v.getProductIds(), productNames, "Sản phẩm"),
                names(v.getCategoryIds(), categoryNames, "Danh mục"), !v.getUserIds().isEmpty())).toList();
    }

    private List<String> names(Set<Long> ids, Map<Long, String> names, String fallback) {
        return ids.stream().sorted(Comparator.naturalOrder())
                .map(id -> names.getOrDefault(id, fallback + " #" + id)).toList();
    }
}
