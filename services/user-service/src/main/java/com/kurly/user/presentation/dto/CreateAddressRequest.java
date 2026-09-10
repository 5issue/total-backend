package com.kurly.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 신규 배송지 등록 요청.
 *
 * <p>길이 제한은 컬럼 길이와 맞춘다. 검증 없이 넘기면 DB에서 잘리거나 예외가 나 500이 된다
 * (시큐어코딩가이드 BE-01).
 */
public record CreateAddressRequest(

        @NotBlank(message = "배송지 이름은 필수입니다.")
        @Size(max = 50, message = "배송지 이름은 50자를 넘을 수 없습니다.")
        String addressName,

        @NotBlank(message = "수취인 이름은 필수입니다.")
        @Size(max = 50, message = "수취인 이름은 50자를 넘을 수 없습니다.")
        String recipientName,

        // 유선전화도 허용하되 자릿수 구조를 강제한다. [0-9-] 만으로는 "---------" 같은
        // 숫자 없는 값도 통과한다.
        @NotBlank(message = "수취인 연락처는 필수입니다.")
        @Pattern(regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
                message = "연락처 형식이 올바르지 않습니다.")
        String phone,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Pattern(regexp = "^[0-9]{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
        String zipCode,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자를 넘을 수 없습니다.")
        String address,

        @Size(max = 255, message = "상세주소는 255자를 넘을 수 없습니다.")
        String addressDetail,

        /**
         * 생략 가능하며 생략은 {@code false}로 본다. {@code boolean}이 아니라 {@code Boolean}인 것은
         * Jackson 3가 {@code FAIL_ON_NULL_FOR_PRIMITIVES}를 기본 활성화해(Jackson 2와 다르다)
         * 값이 없으면 본문 파싱 자체가 실패하기 때문이다.
         */
        Boolean isDefault,

        @Size(max = 255, message = "출입정보는 255자를 넘을 수 없습니다.")
        String accessMethod
) {
}
