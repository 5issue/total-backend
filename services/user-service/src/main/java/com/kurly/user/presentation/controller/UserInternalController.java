package com.kurly.user.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
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
 * 서비스 간 내부 API. 사용자 토큰 없이 호출되며 접근 통제는 NetworkPolicy가 담당한다
 * (인증인가_설계서 3.2·3.3).
 *
 * <p>공통 인증 처리기 기준으로는 {@code @PublicApi}이지만, <b>외부에 노출되어서는 안 되는 경로</b>다.
 * 인그레스에서 {@code /internal/**}을 차단해야 한다.
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserProfileService userProfileService;

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
}
