package com.kurly.order.domain.claim;

import com.kurly.order.domain.common.BaseEntity;
import com.kurly.order.domain.order.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "order_claims")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderClaim extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequesterType requesterType;

    @Column(nullable = false, length = 40)
    private String reasonCode;

    @Column(length = 500)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus refundStatus;

    private Long expectedRefundAmount;

    private Long refundAmount;

    private Long paymentCancelId;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String failureReason;

    @OneToMany(mappedBy = "orderClaim", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundAttachment> attachments = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private OrderClaim(Order order, ClaimType claimType, RequesterType requesterType,
                       String reasonCode, String reasonDetail, Long expectedRefundAmount,
                       LocalDateTime requestedAt) {
        this.order = order;
        this.claimType = claimType;
        this.requesterType = requesterType;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.status = ClaimStatus.REQUESTED;
        this.refundStatus = RefundStatus.NOT_REQUESTED;
        this.expectedRefundAmount = expectedRefundAmount;
        this.requestedAt = requestedAt;
    }

    public static OrderClaim create(Order order, ClaimType claimType, RequesterType requesterType,
                                    String reasonCode, String reasonDetail, Long expectedRefundAmount) {
        Assert.notNull(order, "연관 주문은 필수입니다.");
        Assert.notNull(claimType, "클레임 유형은 필수입니다.");
        Assert.notNull(requesterType, "요청자 구분은 필수입니다.");
        Assert.hasText(reasonCode, "사유 코드는 필수입니다.");

        return OrderClaim.builder()
                .order(order)
                .claimType(claimType)
                .requesterType(requesterType)
                .reasonCode(reasonCode)
                .reasonDetail(reasonDetail)
                .expectedRefundAmount(expectedRefundAmount)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    public void addAttachment(RefundAttachment attachment) {
        this.attachments.add(attachment);
        attachment.assignOrderClaim(this);
    }
}