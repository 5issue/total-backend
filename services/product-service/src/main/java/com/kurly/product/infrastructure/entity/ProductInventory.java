package com.kurly.product.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "base_quantity", nullable = false)
    private Integer baseQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private ProductInventory(Product product, Integer baseQuantity, Integer reservedQuantity) {
        this.product = product;
        this.baseQuantity = baseQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    public int getAvailableQuantity() {
        return baseQuantity - reservedQuantity;
    }

    public void hold(int quantity) {
        this.reservedQuantity += quantity;
    }

    public void release(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }
}
