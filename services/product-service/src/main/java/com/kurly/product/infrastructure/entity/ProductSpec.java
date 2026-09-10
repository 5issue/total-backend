package com.kurly.product.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "product_spec")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 20)
    private StorageType storageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "packaging_type", length = 20)
    private PackagingType packagingType;

    @Column(name = "attributes", columnDefinition = "TEXT")
    private String attributes;

    @Builder
    private ProductSpec(Product product, StorageType storageType, PackagingType packagingType, String attributes) {
        this.product = product;
        this.storageType = storageType;
        this.packagingType = packagingType;
        this.attributes = attributes;
    }

    @Getter
    public enum StorageType {
        REFRIGERATED("냉장"),
        FROZEN("냉동"),
        ROOM_TEMPERATURE("실온");

        private final String label;

        StorageType(String label) {
            this.label = label;
        }
    }

    @Getter
    public enum PackagingType {
        PAPER("종이"),
        PLASTIC("플라스틱"),
        FOAM("스티로폼"),
        CARDBOARD("골판지");

        private final String label;

        PackagingType(String label) {
            this.label = label;
        }
    }
}
