package com.kurly.user.presentation.dto;

/**
 * 기본 주문자 정보 및 배송 요청사항.
 *
 * <p>{@code defaultAddress}는 배송지를 한 건도 등록하지 않은 회원에서 {@code null}이다.
 */
public record UserProfileResponse(String name, DefaultAddressResponse defaultAddress) {
}
