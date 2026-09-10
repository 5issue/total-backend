package com.kurly.user.presentation.dto;

import com.kurly.user.domain.entity.DeliveryAddress;

/**
 * 주문 프로필에 실리는 기본 배송지.
 *
 * <p>목록 항목({@link AddressResponse})과 달리 {@code phone}·{@code isDefault}를 담지 않는다.
 * 주문서 화면에 쓰이지 않는 개인정보를 굳이 내보내지 않기 위함이다(시큐어코딩가이드 BE-17).
 */
public record DefaultAddressResponse(
        Long addressId,
        String addressName,
        String recipientName,
        String zipCode,
        String address,
        String addressDetail,
        String accessMethod
) {

    public static DefaultAddressResponse from(DeliveryAddress a) {
        return new DefaultAddressResponse(
                a.getId(),
                a.getAddressName(),
                a.getRecipientName(),
                a.getZipCode(),
                a.getAddress(),
                a.getAddressDetail(),
                a.getAccessMethod());
    }
}
