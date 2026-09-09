package com.kurly.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 배송지.
 *
 * <p>소유자를 {@code User} 연관관계가 아니라 {@code userId} 값으로 들고 있다. 소유권 검사와
 * 목록 조회 외에 회원 정보가 필요한 지점이 없어 연관관계를 맺을 이유가 없다(DB에는 FK가 있다).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "delivery_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유자. 모든 조회에 이 값을 함께 걸어 타인 리소스 접근을 막는다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "address_name", length = 50)
    private String addressName;

    @Column(name = "recipient_name", length = 50)
    private String recipientName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    /**
     * 기본 배송지 여부. 필드명에 {@code is} 접두사를 쓰지 않는 것은 파생 쿼리 이름과
     * 프로퍼티명을 맞추기 위함이다({@code default}는 예약어라 쓸 수 없다).
     */
    @Column(name = "is_default", nullable = false, columnDefinition = "boolean")
    private boolean defaultAddress;

    @Column(name = "access_method", length = 255)
    private String accessMethod;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DeliveryAddress(Long userId, String addressName, String recipientName, String phone,
                            String zipCode, String address, String addressDetail,
                            boolean defaultAddress, String accessMethod) {
        this.userId = userId;
        this.addressName = addressName;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.defaultAddress = defaultAddress;
        this.accessMethod = accessMethod;
    }

    public void markDefault() {
        this.defaultAddress = true;
    }

    public void unmarkDefault() {
        this.defaultAddress = false;
    }
}
