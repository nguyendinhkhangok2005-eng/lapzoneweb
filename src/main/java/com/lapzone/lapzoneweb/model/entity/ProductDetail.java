package com.lapzone.lapzoneweb.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_details")
@Getter
@Setter
public class ProductDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cpu;
    private String ram;
    private String storage;
    private String gpu;
    private String screen;
    private String battery;
    private String audio;
    private String ports;
    private String wireless;
    private String weight;
    private String color;
    
    @Column(name = "condition_status")
    private String conditionStatus;
    
    private String warranty;
    
    @Column(name = "more_info", columnDefinition = "TEXT")
    private String moreInfo;

    @OneToOne
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;
}