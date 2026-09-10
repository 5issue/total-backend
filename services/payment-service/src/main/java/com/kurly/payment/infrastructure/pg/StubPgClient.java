package com.kurly.payment.infrastructure.pg;

import com.kurly.payment.application.port.PgClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 로컬 전용 PG 스텁.
 *
 * <p>토스페이먼츠 연동은 샌드박스 키와 연동 정책이 확정된 뒤에 붙인다. 그때까지 결제 흐름을
 * 기동·검증할 수 있도록 두는 대역이다.
 *
 * <p><b>local 프로파일에서만 등록된다.</b> 운영에는 이 빈이 없으므로 실제 구현을 붙이기 전에
 * 배포하면 {@code PgClient} 빈을 찾지 못해 기동이 중단된다. 스텁이 실제 결제를 처리하는 사고를
 * 막기 위한 의도적인 fail-closed다.
 */
@Slf4j
@Component
@Profile("local")
public class StubPgClient implements PgClient {

    @Override
    public Approval approve(String paymentKey, Long orderId, long amount) {
        log.warn("PG 스텁으로 승인을 흉내 냅니다. 실제 결제가 아닙니다: orderId={}, amount={}", orderId, amount);
        return new Approval(paymentKey, "CARD", "https://stub.local/receipt/" + orderId);
    }

    @Override
    public Cancellation cancel(String paymentKey, long amount, String reason) {
        log.warn("PG 스텁으로 취소를 흉내 냅니다. 실제 취소가 아닙니다: amount={}, reason={}", amount, reason);
        return new Cancellation("STUB-CANCEL-" + UUID.randomUUID());
    }
}
