package com.kurly.user.presentation.dto;

import com.kurly.user.domain.entity.DeliveryAddress;

/**
 * 서비스 간 배송지 조회 응답. <b>필드 이름은 order-service와 합의한 계약이다</b>(2026-09-12).
 *
 * <p>엔티티와 이름이 다른 둘을 주의한다 — 엔티티의 {@code phone}·{@code addressDetail}이
 * 계약에서는 {@code recipientPhone}·{@code detailAddress}다. 임의로 맞추면 호출측 역직렬화가 깨진다.
 *
 * <p>우편번호({@code zipCode})는 합의 명세에 없어 싣지 않는다. 배송 약속 계산에 필요해지면
 * <b>명세를 먼저 고친 뒤</b> 추가한다.
 */
public record InternalAddressResponse(
        Long addressId,
        String addressName,
        String recipientName,
        String recipientPhone,
        String address,
        String detailAddress
) {

    public static InternalAddressResponse from(DeliveryAddress address) {
        return new InternalAddressResponse(
                address.getId(),
                address.getAddressName(),
                address.getRecipientName(),
                address.getPhone(),
                address.getAddress(),
                address.getAddressDetail());
    }
}
