package com.kurly.user.presentation.dto;

import com.kurly.user.domain.enums.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param name 소셜 제공자가 알려준 이름. <b>필수가 아니다</b> — 제공자 콘솔의 동의 항목이 꺼져 있거나
 *             사용자가 선택 동의를 거부하면 오지 않는다. 길이는 컬럼 정의(50)에 맞춘다.
 */
public record SyncProfileRequest(
        @NotNull(message = "provider는 필수입니다.")
        AuthProvider provider,

        @NotBlank(message = "providerId는 필수입니다.")
        String providerId,

        @Size(max = 50, message = "name은 50자를 넘을 수 없습니다.")
        String name
) {
}
