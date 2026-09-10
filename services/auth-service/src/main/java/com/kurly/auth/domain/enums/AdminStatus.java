package com.kurly.auth.domain.enums;

/**
 * 관리자 계정 생명주기 상태.
 *
 * <p>일시 잠금은 이 enum이 아니라 {@code auth_admins.locked_until}이 단독으로 관리한다.
 * 상태값과 잠금 시각이 모두 잠금을 표현하면 진실 공급원이 둘로 갈라지기 때문이다.
 */
public enum AdminStatus {

    /** 정상 이용 가능 */
    ACTIVE,

    /** 퇴사·권한 회수 등으로 영구 비활성화. 운영자만 되돌릴 수 있다. */
    DISABLED
}
