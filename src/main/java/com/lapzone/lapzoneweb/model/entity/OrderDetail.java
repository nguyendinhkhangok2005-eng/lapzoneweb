package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "order_details")
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Integer quantity;
    
    private Double price; // Giá tại thời điểm mua hàng

    // Mapping về bảng Order
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    // Mapping về bảng Product
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    public OrderDetail() {}

    // Getters và Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}