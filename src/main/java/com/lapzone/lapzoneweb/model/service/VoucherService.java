package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class VoucherService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final VoucherRepository vouchers;
    private final VoucherRedemptionRepository redemptions;
    private final ProductRepository products;
    private final UserRepository users;

    public VoucherService(VoucherRepository vouchers, VoucherRedemptionRepository redemptions,
                          ProductRepository products, UserRepository users) {
        this.vouchers = vouchers;
        this.redemptions = redemptions;
        this.products = products;
        this.users = users;
    }

    public record Quote(BigDecimal subtotal, BigDecimal discount, BigDecimal total, Map<Long, BigDecimal> prices) {}
    public record Prepared(Voucher voucher, Quote quote) {}

    public static String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{3,40}"))
            throw new IllegalArgumentException("Mã giảm giá không hợp lệ.");
        return normalized;
    }

    @Transactional(readOnly = true)
    public Quote preview(String code, User user, List<CartItem> items) {
        requireUser(user);
        Voucher voucher = vouchers.findByCode(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại."));
        long usage = redemptions.countByVoucherIdAndUserIdAndStateNot(voucher.getId(), user.getId(), VoucherRedemption.State.RELEASED);
        return validateAndPrice(voucher, user, items, usage);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Prepared prepare(String code, User user, List<CartItem> items, String checkoutKey) {
        requireUser(user);
        try { if (!UUID.fromString(checkoutKey).toString().equals(checkoutKey)) throw new IllegalArgumentException(); }
        catch (Exception e) { throw new IllegalArgumentException("Phiên thanh toán không hợp lệ. Vui lòng tải lại trang."); }
        Voucher voucher = vouchers.lockByCode(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại."));
        if (redemptions.existsByCheckoutKey(checkoutKey))
            throw new IllegalArgumentException("Yêu cầu thanh toán này đã được xử lý.");
        long usage = redemptions.lockActiveUsage(voucher.getId(), user.getId()).size();
        return new Prepared(voucher, validateAndPrice(voucher, user, items, usage));
    }

    private void requireUser(User user) {
        if (user == null || user.getId() == null || !users.existsById(user.getId()))
            throw new IllegalArgumentException("Vui lòng đăng nhập để sử dụng mã giảm giá.");
    }

    private Quote validateAndPrice(Voucher voucher, User user, List<CartItem> items, long usage) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (!voucher.isActive())
            throw new IllegalArgumentException("Mã giảm giá đã ngừng hoạt động.");
        if (now.isBefore(voucher.getStartsAt()))
            throw new IllegalArgumentException("Mã giảm giá chưa có hiệu lực.");
        if (!now.isBefore(voucher.getEndsAt()))
            throw new IllegalArgumentException("Mã giảm giá đã hết hạn.");
        if (voucher.getAvailableQuantity() <= 0)
            throw new IllegalArgumentException("Mã giảm giá đã hết lượt sử dụng.");
        if (!voucher.getUserIds().isEmpty() && !voucher.getUserIds().contains(user.getId()))
            throw new IllegalArgumentException("Tài khoản không đủ điều kiện sử dụng mã giảm giá.");
        if (usage >= voucher.getPerUserLimit())
            throw new IllegalArgumentException("Bạn đã đạt giới hạn sử dụng mã giảm giá.");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Đơn hàng chưa có sản phẩm.");
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal eligible = BigDecimal.ZERO;
        Map<Long, BigDecimal> prices = new HashMap<>();
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null || item.getQuantity() == null || item.getQuantity() < 1)
                throw new IllegalArgumentException("Sản phẩm hoặc số lượng không hợp lệ.");
            Product product = products.findById(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không còn tồn tại."));
            Double sale = PromotionService.getDiscountedPrice(product);
            if (sale == null || !Double.isFinite(sale) || sale < 0)
                throw new IllegalArgumentException("Giá sản phẩm không hợp lệ.");
            BigDecimal price = BigDecimal.valueOf(sale);
            prices.put(product.getId(), price);
            BigDecimal line = price.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(line);
            boolean unrestricted = voucher.getProductIds().isEmpty() && voucher.getCategoryIds().isEmpty();
            boolean matches = voucher.getProductIds().contains(product.getId())
                    || (product.getCategory() != null && voucher.getCategoryIds().contains(product.getCategory().getId()));
            if (unrestricted || matches) eligible = eligible.add(line);
        }
        if (subtotal.compareTo(voucher.getMinimumOrderValue()) < 0)
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu của mã giảm giá.");
        if (eligible.signum() <= 0)
            throw new IllegalArgumentException("Đơn hàng không có sản phẩm áp dụng mã giảm giá.");
        BigDecimal discount = eligible.multiply(BigDecimal.valueOf(voucher.getDiscountPercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).min(eligible);
        if (discount.signum() <= 0) throw new IllegalArgumentException("Giá trị giảm giá chưa đủ một đồng.");
        return new Quote(subtotal, discount, subtotal.subtract(discount), Map.copyOf(prices));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Prepared prepared, User user, Order order, String checkoutKey) {
        Voucher voucher = prepared.voucher();
        VoucherRedemption redemption = new VoucherRedemption();
        redemption.setVoucher(voucher);
        redemption.setUser(user);
        redemption.setOrder(order);
        redemption.setCheckoutKey(checkoutKey);
        redemption.setDiscountAmount(prepared.quote().discount());
        redemption.setCreatedAt(LocalDateTime.now(ZONE));
        voucher.setReservedCount(voucher.getReservedCount() + 1);
        vouchers.save(voucher);
        // Database unique constraints are the final guard against duplicate requests/orders.
        redemptions.saveAndFlush(redemption);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onOrderStatus(Order order, String newStatus) {
        if (order.getVoucherCode() == null) return;
        VoucherRedemption redemption = redemptions.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch sử sử dụng mã giảm giá."));
        Voucher voucher = vouchers.lockById(redemption.getVoucher().getId()).orElseThrow();
        if (redemption.getState() == VoucherRedemption.State.REDEEMED
                && Set.of("CHỜ THANH TOÁN", "THANH TOÁN THẤT BẠI").contains(newStatus))
            throw new IllegalArgumentException("Đơn đã thanh toán không được chuyển về chưa thanh toán.");
        if (redemption.getState() != VoucherRedemption.State.RESERVED) return;
        if (Set.of("ĐÃ HỦY", "THANH TOÁN THẤT BẠI").contains(newStatus)) {
            redemption.setState(VoucherRedemption.State.RELEASED);
            redemption.setReleasedAt(LocalDateTime.now(ZONE));
            voucher.setReservedCount(voucher.getReservedCount() - 1);
        } else if (Set.of("ĐÃ THANH TOÁN", "HOÀN THÀNH").contains(newStatus)) {
            redemption.setState(VoucherRedemption.State.REDEEMED);
            redemption.setRedeemedAt(LocalDateTime.now(ZONE));
            voucher.setReservedCount(voucher.getReservedCount() - 1);
            voucher.setRedeemedCount(voucher.getRedeemedCount() + 1);
        }
        vouchers.save(voucher);
        redemptions.save(redemption);
    }
}
