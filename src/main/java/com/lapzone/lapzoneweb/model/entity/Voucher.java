package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "vouchers")
@Check(constraints = "discount_percent between 1 and 10 and quantity >= 0 and reserved_count >= 0 and redeemed_count >= 0 and reserved_count + redeemed_count <= quantity and per_user_limit >= 1 and minimum_order_value >= 0 and ends_at > starts_at")
@Getter @Setter
public class Voucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false)
    private int discountPercent;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private int reservedCount;
    @Column(nullable = false)
    private int redeemedCount;
    @Column(nullable = false)
    private int perUserLimit = 1;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private LocalDateTime startsAt;
    @Column(nullable = false)
    private LocalDateTime endsAt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumOrderValue = BigDecimal.ZERO;
    @ElementCollection
    @CollectionTable(name = "voucher_products", joinColumns = @JoinColumn(name = "voucher_id"))
    @Column(name = "product_id")
    private Set<Long> productIds = new HashSet<>();
    @ElementCollection
    @CollectionTable(name = "voucher_categories", joinColumns = @JoinColumn(name = "voucher_id"))
    @Column(name = "category_id")
    private Set<Long> categoryIds = new HashSet<>();
    @ElementCollection
    @CollectionTable(name = "voucher_users", joinColumns = @JoinColumn(name = "voucher_id"))
    @Column(name = "user_id")
    private Set<Long> userIds = new HashSet<>();

    public int getAvailableQuantity() { return quantity - reservedCount - redeemedCount; }
}
