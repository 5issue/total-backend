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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "media_url", length = 512, nullable = false)
    private String mediaUrl;

    @Column(name = "s3_key", length = 255)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 10)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_role", length = 20)
    private MediaRole mediaRole;

    @Column(name = "sequence")
    private Integer sequence;

    @Builder
    private ProductMedia(Product product, String mediaUrl, String s3Key,
                          MediaType mediaType, MediaRole mediaRole, Integer sequence) {
        this.product = product;
        this.mediaUrl = mediaUrl;
        this.s3Key = s3Key;
        this.mediaType = mediaType;
        this.mediaRole = mediaRole;
        this.sequence = sequence;
    }

    public enum MediaType {
        IMAGE,
        VIDEO
    }

    public enum MediaRole {
        THUMBNAIL,
        DETAIL
    }
}
