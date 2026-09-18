package com.kurly.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param returnTo 로그인 후 복귀할 <b>내부 상대 경로</b>. 선택값이며 검증을 통과하지 못하면 버린다
 *                 (검토요청서 A-8). 값이 이상하다고 로그인을 막지는 않는다.
 */
public record SocialLoginUrlRequest(
        @NotBlank(message = "redirectUri는 필수입니다.")
        String redirectUri,
        String returnTo
) {
}
