package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.VoucherForm;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class VoucherAdminService {
    private final VoucherRepository vouchers;
    private final VoucherRedemptionRepository redemptions;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final Validator validator;

    public VoucherAdminService(VoucherRepository vouchers, VoucherRedemptionRepository redemptions,
            ProductRepository products, CategoryRepository categories, UserRepository users, Validator validator) {
        this.vouchers = vouchers; this.redemptions = redemptions; this.products = products;
        this.categories = categories; this.users = users; this.validator = validator;
    }

    @Transactional(readOnly = true)
    public Page<Voucher> list(int page) {
        return vouchers.findAll(PageRequest.of(Math.max(0, page), 20, Sort.by("id").descending()));
    }

    @Transactional(readOnly = true)
    public VoucherForm form(Long id) {
        Voucher v = vouchers.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        VoucherForm f = new VoucherForm();
        f.setCode(v.getCode()); f.setDiscountPercent(v.getDiscountPercent()); f.setQuantity(v.getQuantity());
        f.setPerUserLimit(v.getPerUserLimit()); f.setActive(v.isActive()); f.setStartsAt(v.getStartsAt());
        f.setEndsAt(v.getEndsAt()); f.setMinimumOrderValue(v.getMinimumOrderValue());
        f.setProductIds(new HashSet<>(v.getProductIds())); f.setCategoryIds(new HashSet<>(v.getCategoryIds()));
        f.setUserIds(new HashSet<>(v.getUserIds()));
        return f;
    }

    @Transactional
    public void save(Long id, VoucherForm f) {
        if (f == null || !validator.validate(f).isEmpty() || !f.getEndsAt().isAfter(f.getStartsAt()))
            throw new IllegalArgumentException("Kiểm tra mã, mức giảm 1–10%, số lượng, giới hạn và thời gian hiệu lực.");
        String code = VoucherService.normalizeCode(f.getCode());
        Voucher v = id == null ? new Voucher() : vouchers.lockById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        if (id == null ? vouchers.existsByCode(code) : vouchers.existsByCodeAndIdNot(code, id))
            throw new IllegalArgumentException("Mã giảm giá đã tồn tại.");
        if (f.getQuantity() < v.getReservedCount() + v.getRedeemedCount())
            throw new IllegalArgumentException("Số lượng không được thấp hơn tổng lượt đang giữ và đã dùng.");
        Set<Long> productIds = f.getProductIds() == null ? Set.of() : f.getProductIds();
        Set<Long> categoryIds = f.getCategoryIds() == null ? Set.of() : f.getCategoryIds();
        Set<Long> userIds = f.getUserIds() == null ? Set.of() : f.getUserIds();
        if (products.findAllById(productIds).size() != productIds.size()
                || categories.findAllById(categoryIds).size() != categoryIds.size()
                || users.findAllById(userIds).size() != userIds.size())
            throw new IllegalArgumentException("Có sản phẩm, danh mục hoặc tài khoản không tồn tại.");
        v.setCode(code); v.setDiscountPercent(f.getDiscountPercent()); v.setQuantity(f.getQuantity());
        v.setPerUserLimit(f.getPerUserLimit()); v.setActive(f.isActive()); v.setStartsAt(f.getStartsAt());
        v.setEndsAt(f.getEndsAt()); v.setMinimumOrderValue(f.getMinimumOrderValue());
        v.setProductIds(new HashSet<>(productIds)); v.setCategoryIds(new HashSet<>(categoryIds)); v.setUserIds(new HashSet<>(userIds));
        vouchers.saveAndFlush(v);
    }

    @Transactional
    public void deactivate(Long id) {
        Voucher v = vouchers.lockById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá."));
        v.setActive(false);
        vouchers.save(v);
    }

    public record Usage(Long orderId, Long userId, String state, java.math.BigDecimal discount,
                        java.time.LocalDateTime createdAt) {}
    @Transactional(readOnly = true)
    public Page<Usage> history(Long id, int page) {
        return redemptions.findByVoucherIdOrderByIdDesc(id, PageRequest.of(Math.max(0, page), 20))
            .map(r -> new Usage(r.getOrder().getId(), r.getUser().getId(),
                switch (r.getState()) { case RESERVED -> "Đang giữ lượt"; case REDEEMED -> "Đã sử dụng"; case RELEASED -> "Đã hoàn lượt"; },
                r.getDiscountAmount(), r.getCreatedAt()));
    }
}
