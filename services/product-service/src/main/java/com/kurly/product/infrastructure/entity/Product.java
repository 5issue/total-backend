package com.kurly.product.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_code", length = 50)
    private String skuCode;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", length = 225, nullable = false)
    private String name;

    @Column(name = "short_description", length = 225)
    private String shortDescription;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "discount_rate")
    private Integer discountRate;

    @Column(name = "sale_price")
    private Long salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 10)
    private ProductType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10)
    private ProductStatus status;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "total_sales_count")
    private Long totalSalesCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Product(String skuCode, Long parentId, String name, String brand, Long price,
                     Integer discountRate, Long salePrice, ProductType type, ProductStatus status,
                     Integer likeCount, Long totalSalesCount) {
        this.skuCode = skuCode;
        this.parentId = parentId;
        this.name = name;
        this.brand = brand;
        this.price = price;
        this.discountRate = discountRate;
        this.salePrice = salePrice;
        this.type = type;
        this.status = status;
        this.likeCount = likeCount;
        this.totalSalesCount = totalSalesCount;
    }

    public enum ProductStatus {
        SALE, SOLDOUT, HIDDEN
    }
    public enum ProductType {
        GROUP, UNIT
    }
}
