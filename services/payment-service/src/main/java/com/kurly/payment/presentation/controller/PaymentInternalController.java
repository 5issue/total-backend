package com.kurly.payment.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.payment.application.IdempotentRequestExecutor;
import com.kurly.payment.application.PaymentCancelService;
import com.kurly.payment.presentation.dto.CancelRequest;
import com.kurly.payment.presentation.dto.CancelResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 내부 결제 API.
 *
 * <p>고객이 마이페이지에서 취소를 누르면 주문 서비스가 취소 가능 여부(OMS)를 확인한 뒤 이 엔드포인트를
 * 호출한다(주문-결제 시퀀스 2절). <b>호출 시 사용자의 access token을 그대로 전파하고</b>
 * payment-service가 재검증한다(인증인가_설계서 3.4). 그래서 {@code @Authenticated}이며 소유권 검사가
 * 성립한다.
 *
 * <p>{@code /api/v1/}과의 차이는 <b>인그레스에서 외부 노출을 막는다는 것뿐</b>이다.
 * {@code /internal/**}은 반드시 차단해야 한다.
 *
 * <p>반품 승인에 따른 환불은 이 경로가 아니라 {@code payment.refund.queue} 메시지로 들어온다.
 * 그쪽은 행위자가 관리자라 사용자 토큰이 없으므로 별도 소비자가 처리한다.
 */
@RestController
@RequestMapping("/internal/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentInternalController {

    private static final String CANCEL_PATH_FORMAT = "/internal/v1/payments/%d/cancel";

    private final PaymentCancelService paymentCancelService;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    /** 결제 취소. 없는 결제와 타인의 결제는 같은 404다. */
    @Authenticated
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<CancelResponse>> cancel(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable Long paymentId,
            @RequestHeader(PaymentController.IDEMPOTENCY_KEY_HEADER)
            @NotBlank(message = "Idempotency-Key는 필수입니다.") String idempotencyKey,
            @Valid @RequestBody CancelRequest request) {

        var outcome = idempotentRequestExecutor.execute(
                me.userId(), idempotencyKey, CANCEL_PATH_FORMAT.formatted(paymentId), request,
                CancelResponse.class, HttpStatus.OK.value(),
                () -> CancelResponse.from(paymentCancelService.cancel(
                        paymentId, me.userId(), request.cancelReason())));

        return ResponseEntity.status(outcome.status())
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("결제가 정상적으로 취소되었습니다.", outcome.body()));
    }
}
