package com.kurly.payment.application;

import com.kurly.payment.domain.entity.IdempotencyKey;
import com.kurly.payment.domain.repository.IdempotencyKeyRepository;
import com.kurly.payment.exception.DuplicatePaymentRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 멱등키 처리.
 *
 * <p>결제는 같은 요청이 두 번 처리되면 곧 이중 결제다. 네트워크 재시도·사용자의 새로고침으로
 * 중복 요청은 반드시 발생하므로, 클라이언트가 보낸 키로 한 번만 처리되도록 보장한다.
 *
 * <p>핵심은 <b>처리 전에 키를 먼저 기록</b>하는 것이다. 완료된 응답만 저장하면 진행 중인 요청과
 * 아예 없는 요청을 구분하지 못해 동시에 들어온 두 요청이 모두 결제로 넘어간다.
 * 유니크 제약 위반이 곧 "이미 진행 중"의 신호가 되어 별도 잠금이 필요 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    /**
     * 처리 시작을 선점한다.
     *
     * <p><b>별도 트랜잭션에서 커밋한다.</b> 호출부의 트랜잭션에 얹으면 결제가 실패해 롤백될 때
     * 선점 기록까지 사라져, 재시도가 중복으로 통과한다.
     *
     * @return 이 호출이 선점에 성공했으면 새 기록. 이미 완료된 요청이면 그 기록(응답 재생용)
     * @throws DuplicatePaymentRequestException 같은 키가 처리 중이거나 다른 요청에 재사용된 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result begin(Long userId, String key, String requestPath, String requestFingerprint) {
        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestPath, requestFingerprint);
        }
        try {
            IdempotencyKey created = idempotencyKeyRepository.save(IdempotencyKey.builder()
                    .userId(userId)
                    .idempotencyKey(key)
                    .requestPath(requestPath)
                    .requestFingerprint(requestFingerprint)
                    .build());
            return new Result(created, false);
        } catch (DataIntegrityViolationException e) {
            // 조회와 저장 사이에 같은 키가 끼어들었다. 유니크 제약이 승자를 정한다.
            log.info("멱등키 동시 선점 감지: userId={}", userId);
            IdempotencyKey winner = idempotencyKeyRepository
                    .findByUserIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() -> e);
            return replayOrReject(winner, requestPath, requestFingerprint);
        }
    }

    /**
     * 처리 결과를 확정한다. 같은 키로 다시 오면 이 응답이 그대로 재생된다.
     *
     * <p>선점과 마찬가지로 별도 트랜잭션에서 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long recordId, int responseStatus, String responseBody) {
        idempotencyKeyRepository.findById(recordId)
                .ifPresent(record -> record.complete(responseStatus, responseBody));
    }

    /**
     * 선점을 해제해 같은 키로 다시 시도할 수 있게 한다.
     *
     * <p><b>결제가 일어나지 않은 것이 확실할 때만 호출해야 한다.</b> 결제 여부를 모르는 상태에서
     * 해제하면 클라이언트의 재시도가 이중 결제가 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long recordId) {
        idempotencyKeyRepository.deleteById(recordId);
    }

    private Result replayOrReject(IdempotencyKey record, String requestPath, String requestFingerprint) {
        if (record.conflictsWith(requestPath, requestFingerprint)) {
            // 같은 키를 다른 요청에 재사용했다. 클라이언트 오류이므로 응답을 재생하지 않는다.
            log.warn("멱등키가 다른 요청에 재사용됨: userId={}, path={}", record.getUserId(), requestPath);
            throw new DuplicatePaymentRequestException();
        }
        if (!record.isCompleted()) {
            // 앞선 요청이 아직 처리 중이다. 결과를 알 수 없으므로 새로 처리하지 않는다.
            throw new DuplicatePaymentRequestException();
        }
        return new Result(record, true);
    }

    /**
     * @param replay {@code true}면 저장된 응답을 그대로 돌려줘야 한다. 결제를 다시 시도하면 안 된다
     */
    public record Result(IdempotencyKey record, boolean replay) {
    }
}
