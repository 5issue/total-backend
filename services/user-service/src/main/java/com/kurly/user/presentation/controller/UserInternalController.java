package com.kurly.user.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import com.kurly.user.presentation.dto.InternalAddressResponse;
import com.kurly.user.exception.AddressNotFoundException;
import com.kurly.user.application.DeliveryAddressService;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.user.application.UserProfileService;
import com.kurly.user.presentation.dto.SyncProfileRequest;
import com.kurly.user.presentation.dto.SyncProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 내부 API. <b>외부에 노출되어서는 안 되는 경로</b>이므로 인그레스에서
 * {@code /internal/**}을 차단해야 한다.
 *
 * <p>엔드포인트마다 방어 수준이 다르다.
 * <ul>
 *   <li>{@code sync-profile} — 사용자 토큰 없이 호출된다. {@code @PublicApi}이고
 *       <b>접근 통제가 NetworkPolicy뿐</b>이다(설계서 3.2·3.3)</li>
 *   <li>배송지 조회 — 호출측이 <b>사용자 토큰을 전파</b>하므로 {@code @Authenticated}이고
 *       소유권 검사가 성립한다(설계서 3.4)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserProfileService userProfileService;
    private final DeliveryAddressService deliveryAddressService;

    /** 소셜 로그인 시 회원 프로필을 동기화한다. 신규 생성이면 201, 기존 회원이면 200. */
    @PublicApi
    @PostMapping("/sync-profile")
    public ResponseEntity<ApiResponse<SyncProfileResponse>> syncProfile(
            @Valid @RequestBody SyncProfileRequest request) {

        UserProfileService.SyncResult result =
                userProfileService.syncProfile(request.provider(), request.providerId());

        return ResponseEntity.status(result.newUser() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success("회원 프로필 동기화가 완료되었습니다.",
                        SyncProfileResponse.of(result.user(), result.newUser())));
    }

    /**
     * 회원 배송지 단건 조회. 주문 서비스가 장바구니 조회·배송 약속 계산에 쓴다(2026-09-12 합의).
     *
     * <p><b>경로의 {@code memberId}를 믿지 않는다.</b> 전파된 토큰의 주체와 다르면 거절한다.
     * 검사하지 않으면 인증된 사용자가 {@code memberId}만 바꿔 <b>타인의 주소·연락처를 읽을 수
     * 있다.</b> 조회 자체도 소유자 조건을 함께 걸어 두 겹으로 막는다.
     *
     * <p>타인의 배송지와 없는 배송지는 <b>같은 404</b>다. 구분해 응답하면 id를 훑어 존재 여부를
     * 알아낼 수 있다.
     */
    @Authenticated
    @GetMapping("/{memberId}/delivery-addresses/{addressId}")
    public ResponseEntity<ApiResponse<InternalAddressResponse>> deliveryAddress(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable Long memberId,
            @PathVariable Long addressId) {

        if (!me.userId().equals(memberId)) {
            throw new AddressNotFoundException();
        }

        return ResponseEntity.ok(ApiResponse.success("회원 배송지 조회가 완료되었습니다.",
                InternalAddressResponse.from(deliveryAddressService.findOwned(memberId, addressId))));
    }
}
