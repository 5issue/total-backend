package com.kurly.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 결제 부수 테이블 정리.
 *
 * <p>멱등키와 아웃박스는 결제 한 건마다 행이 늘고 <b>스스로 줄지 않는다.</b> 두면 조회가 느려지고
 * 저장 비용이 계속 는다. 소임을 다한 행을 지우는 것이 여기의 일이다.
 *
 * <p>{@code FAILED} 아웃박스와 {@code FAILED} 재시도는 지우지 않는다. 사람이 봐야 하는 상태이고,
 * 정리 배치가 조용히 치워버리면 무엇이 잘못됐는지 알 방법이 사라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMaintenanceService {

    /** 한 주기가 테이블을 지나치게 오래 붙잡지 않도록 두는 반복 상한. */
    private static final int MAX_PURGE_ROUNDS = 20;

    private final PaymentRecordService paymentRecordService;

    /**
     * 매달린 멱등키 선점을 푼다.
     *
     * <p>처리 도중 프로세스가 죽으면 {@code IN_PROGRESS} 표시만 남는다. 그 키로 오는 재요청은
     * 영원히 409가 되어 고객이 결제를 다시 시도할 방법이 없어진다. 결제 자체의 진실은 PG 대사가
     * 맞춰 놓으므로({@link PaymentReconciliationService}), 여기서 푸는 것은 <b>잠금뿐</b>이다.
     *
     * @param staleAfter 대사 유예보다 넉넉히 길게 잡는다. 대사가 결론을 내기 전에 잠금을 풀면
     *                   재요청이 아직 정리되지 않은 결제와 겹친다
     */
    public void releaseStaleIdempotencyKeys(int limit, Duration staleAfter) {
        int released = paymentRecordService.deleteStaleIdempotencyKeys(
                LocalDateTime.now().minus(staleAfter), limit);
        if (released > 0) {
            log.warn("매달린 멱등키 선점 해제: {}건", released);
        }
    }

    /**
     * 보관 기간이 지난 완료 멱등키를 지운다.
     *
     * <p><b>보관 기간을 짧게 잡으면 안 된다.</b> 키가 사라진 뒤 같은 키로 재요청이 오면 재생할
     * 응답이 없어 결제가 새로 실행된다. 클라이언트 재시도는 길어야 분 단위이므로 일 단위 보관이면
     * 겹칠 일이 없다. 설령 겹치더라도 같은 주문에 두 번째 성공을 막는 DB 유니크 제약이 마지막
     * 방어선으로 남아 있고, 그 경우 승인분은 보상 취소된다.
     */
    public void purgeExpiredIdempotencyKeys(int limit, Duration retention) {
        LocalDateTime before = LocalDateTime.now().minus(retention);
        int purged = purgeInBatches("멱등키",
                () -> paymentRecordService.deleteCompletedIdempotencyKeys(before, limit), limit);
        if (purged > 0) {
            log.info("보관 기간이 지난 멱등키 정리: {}건", purged);
        }
    }

    /** 발행을 마친 지 오래된 아웃박스 이벤트를 지운다. 발행 워커의 조회 대상이 그만큼 줄어든다. */
    public void purgePublishedOutbox(int limit, Duration retention) {
        LocalDateTime before = LocalDateTime.now().minus(retention);
        int purged = purgeInBatches("아웃박스",
                () -> paymentRecordService.deletePublishedOutbox(before, limit), limit);
        if (purged > 0) {
            log.info("발행 완료 아웃박스 정리: {}건", purged);
        }
    }

    /**
     * 지울 것이 남지 않을 때까지 묶음 단위로 반복한다.
     *
     * <p>한 주기에 한 묶음만 지우면 <b>쌓이는 속도를 따라잡지 못한다.</b> 하루 한 번 500건씩 지우는데
     * 하루에 500건 넘게 만료되면 잔여량이 계속 는다.
     *
     * <p>반복 상한을 둔다. 상한이 없으면 한 번의 주기가 테이블을 오래 붙잡고, 밀린 양이 많을 때
     * 다른 워커의 커넥션까지 말라붙는다. 남은 것은 다음 주기가 이어서 지운다.
     */
    private int purgeInBatches(String what, java.util.function.IntSupplier deleteBatch, int limit) {
        int total = 0;
        for (int round = 0; round < MAX_PURGE_ROUNDS; round++) {
            int deleted = deleteBatch.getAsInt();
            total += deleted;
            // 묶음 크기를 못 채웠다면 더 지울 것이 없다.
            if (deleted < limit) {
                return total;
            }
        }
        log.warn("{} 정리가 상한({}회)에 걸려 중단됐다. 남은 분은 다음 주기가 이어받는다: 이번에 {}건 삭제",
                what, MAX_PURGE_ROUNDS, total);
        return total;
    }
}
