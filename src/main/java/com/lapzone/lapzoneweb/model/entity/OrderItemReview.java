package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** A review belongs to a purchased line, rather than merely to the current product. */
@Entity @Table(name = "order_item_reviews")
@org.hibernate.annotations.Check(constraints = "rating between 1 and 5")
@Getter @Setter
public class OrderItemReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_detail_id", nullable = false, unique = true) private OrderDetail orderDetail;
    @Column(nullable = false) private int rating;
    @Column(length = 4000) private String content;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
