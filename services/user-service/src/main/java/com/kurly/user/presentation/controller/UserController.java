package com.kurly.user.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.user.application.DeliveryAddressService;
import com.kurly.user.application.UserProfileService;
import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.presentation.dto.AddressListResponse;
import com.kurly.user.presentation.dto.CreateAddressRequest;
import com.kurly.user.presentation.dto.CreateAddressResponse;
import com.kurly.user.presentation.dto.DefaultAddressUpdateResponse;
import com.kurly.user.presentation.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 본인 API.
 *
 * <p>대상 회원은 항상 토큰의 {@code sub}에서 온다. 경로나 본문으로 받은 회원 식별자는 쓰지 않는다
 * (시큐어코딩가이드 BE-06).
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final DeliveryAddressService deliveryAddressService;

    /** 기본 주문자 정보 및 배송 요청사항 조회. */
    @Authenticated
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthPrincipal AuthenticatedPrincipal me) {

        return ResponseEntity.ok(ApiResponse.success(
                "주문 프로필 정보가 조회되었습니다.", userProfileService.getProfile(me.userId())));
    }

    /** 배송지 목록 조회. */
    @Authenticated
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<AddressListResponse>> getAddresses(
            @AuthPrincipal AuthenticatedPrincipal me) {

        return ResponseEntity.ok(ApiResponse.success(
                "배송지 목록 조회가 완료되었습니다.",
                AddressListResponse.from(deliveryAddressService.findAll(me.userId()))));
    }

    /** 신규 배송지 등록. */
    @Authenticated
    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<CreateAddressResponse>> createAddress(
            @AuthPrincipal AuthenticatedPrincipal me,
            @Valid @RequestBody CreateAddressRequest request) {

        DeliveryAddress created = deliveryAddressService.create(me.userId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("신규 배송지가 등록되었습니다.",
                        CreateAddressResponse.of(created.getId())));
    }

    /** 특정 배송지를 기본 배송지로 설정. 없거나 타인 소유면 404. */
    @Authenticated
    @PatchMapping("/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<DefaultAddressUpdateResponse>> setDefaultAddress(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable Long addressId) {

        DeliveryAddress updated = deliveryAddressService.setDefault(me.userId(), addressId);

        return ResponseEntity.ok(ApiResponse.success("기본 배송지가 변경되었습니다.",
                DefaultAddressUpdateResponse.of(updated.getId())));
    }
}
