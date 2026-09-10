package com.kurly.payment.presentation.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 응답에 실을 시각 변환.
 *
 * <p>엔티티는 {@code LocalDateTime}으로 저장하는데 응답 규약은 UTC ISO-8601이다. 저장 시점에
 * 시스템 시간대로 기록했으므로 되돌릴 때도 같은 시간대를 기준으로 삼는다. 시간대를 다르게 잡으면
 * 승인 시각이 몇 시간씩 어긋난 채 영수증에 찍힌다.
 */
final class Timestamps {

    private Timestamps() {
    }

    static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
