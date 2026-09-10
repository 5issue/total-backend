package com.kurly.product.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "category_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "media_url", length = 512, nullable = false)
    private String mediaUrl;

    @Column(name = "s3_key", length = 255)
    private String s3Key;

    @Builder
    private CategoryMedia(Category category, String mediaUrl, String s3Key) {
        this.category = category;
        this.mediaUrl = mediaUrl;
        this.s3Key = s3Key;
    }
}
