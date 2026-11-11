package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Check(constraints = "discount_amount > 0")
@Table(name = "voucher_redemptions", indexes = @Index(name = "idx_voucher_user", columnList = "voucher_id,user_id"))
@Getter @Setter
public class VoucherRedemption {
    public enum State { RESERVED, REDEEMED, RELEASED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @OneToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
    @Column(nullable = false, unique = true, length = 36)
    private String checkoutKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private State state = State.RESERVED;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime redeemedAt;
    private LocalDateTime releasedAt;
}
