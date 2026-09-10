package com.kurly.payment.infrastructure.pg;

import com.kurly.payment.application.port.PgClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 로컬 전용 PG 스텁.
 *
 * <p>샌드박스 키 없이도 결제 흐름을 기동·검증할 수 있도록 두는 대역이다.
 * 실제 연동은 {@link TossPgClient}가 담당한다.
 *
 * <p><b>{@code payment.pg.client=stub}일 때만 등록된다.</b> 기본값은 실제 토스 연동이라 설정을
 * 빠뜨려도 스텁이 끼어들지 않는다. 스텁이 실제 결제를 대신 처리하는 사고를 막기 위해 켜는 쪽을
 * 명시적으로 만들었다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.pg.client", havingValue = "stub")
public class StubPgClient implements PgClient {

    @Override
    public Approval approve(String paymentKey, Long orderId, long amount) {
        log.warn("PG 스텁으로 승인을 흉내 냅니다. 실제 결제가 아닙니다: orderId={}, amount={}", orderId, amount);
        return new Approval(paymentKey, "CARD", "https://stub.local/receipt/" + orderId);
    }

    /** 스텁은 조회 대상이 없다고 답한다. 대사 배치가 "승인되지 않음"으로 판단하게 된다. */
    @Override
    public java.util.Optional<Inquiry> findByOrderId(Long orderId) {
        log.warn("PG 스텁 조회. 실제 결제 상태가 아닙니다: orderId={}", orderId);
        return java.util.Optional.empty();
    }

    @Override
    public Cancellation cancel(String paymentKey, long amount, String reason, Long cancelId) {
        log.warn("PG 스텁으로 취소를 흉내 냅니다. 실제 취소가 아닙니다: amount={}, reason={}", amount, reason);
        return new Cancellation("STUB-CANCEL-" + UUID.randomUUID());
    }
}
