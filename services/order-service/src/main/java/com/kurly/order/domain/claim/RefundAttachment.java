package com.kurly.order.domain.claim;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@Entity
@Table(name = "refund_attachments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RefundAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderClaimId;

    @Column(nullable = false, length = 100)
    private String s3Bucket;

    @Column(nullable = false, unique = true, length = 500)
    private String s3ObjectKey;

    private String originalFileName;

    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
