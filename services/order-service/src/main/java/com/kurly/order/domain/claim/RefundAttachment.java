package com.kurly.order.domain.claim;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "refund_attachments")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_claim_id", nullable = false)
    private OrderClaim orderClaim;

    @Column(nullable = false, length = 100)
    private String s3Bucket;

    @Column(nullable = false, unique = true, length = 500)
    private String s3ObjectKey;

    private String originalFileName;

    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RefundAttachment(String s3Bucket, String s3ObjectKey, String originalFileName,
                             String contentType, Long fileSize) {
        this.s3Bucket = s3Bucket;
        this.s3ObjectKey = s3ObjectKey;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public static RefundAttachment create(String s3Bucket, String s3ObjectKey, String originalFileName,
                                          String contentType, Long fileSize) {
        Assert.hasText(s3Bucket, "S3 버킷명은 필수입니다.");
        Assert.hasText(s3ObjectKey, "S3 오브젝트 키는 필수입니다.");

        return RefundAttachment.builder()
                .s3Bucket(s3Bucket)
                .s3ObjectKey(s3ObjectKey)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .fileSize(fileSize)
                .build();
    }

    void assignOrderClaim(OrderClaim orderClaim) {
        this.orderClaim = orderClaim;
    }
}