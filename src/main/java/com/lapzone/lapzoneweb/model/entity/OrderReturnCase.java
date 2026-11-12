package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.Objects;

/** Request management only; this entity does not execute a refund. */
@Entity @Table(name = "order_return_cases", indexes = @Index(name = "idx_order_return_cases", columnList = "order_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_return_purchased_line", columnNames = "order_detail_id"))
@org.hibernate.annotations.Check(constraints = "quantity is null or quantity > 0")
@Getter @Setter
public class OrderReturnCase {
    public enum Type { RETURN, REFUND }
    public enum Status { REQUESTED, APPROVED, REJECTED, COMPLETED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false) private Order order;
    // Null means an order-level case; otherwise it must reference a line of this order.
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_detail_id") private OrderDetail orderDetail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Type type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.REQUESTED;
    @Column(length = 2000) private String reason;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    // Nullable for historical order-level cases; new item requests always specify a quantity.
    private Integer quantity;
    @Column(name = "admin_note", length = 2000) private String adminNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private User reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "completed_by") private User completedBy;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "completion_note", length = 2000) private String completionNote;

    @PrePersist @PreUpdate
    void validateLineOwnership() {
        if (orderDetail != null && (order == null || orderDetail.getOrder() == null
                || !Objects.equals(order.getId(), orderDetail.getOrder().getId())))
            throw new IllegalArgumentException("Sản phẩm trả hàng không thuộc đơn hàng.");
    }
}
