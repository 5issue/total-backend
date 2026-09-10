package com.kurly.payment.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.payment.application.IdempotentRequestExecutor;
import com.kurly.payment.application.PaymentCheckoutService;
import com.kurly.payment.application.PaymentQueryService;
import com.kurly.payment.presentation.dto.CheckoutRequest;
import com.kurly.payment.presentation.dto.CheckoutResponse;
import com.kurly.payment.presentation.dto.ReceiptResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API.
 *
 * <p>결제 주체는 항상 토큰의 {@code sub}에서 온다. 경로나 본문으로 받은 사용자 식별자는 쓰지 않는다
 * (시큐어코딩가이드 BE-06).
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CHECKOUT_PATH = "/api/v1/payments/checkout";

    private final PaymentCheckoutService paymentCheckoutService;
    private final PaymentQueryService paymentQueryService;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    /**
     * 결제 승인.
     *
     * <p>{@code Idempotency-Key}로 감싸 같은 요청이 두 번 승인되지 않게 한다. 재요청이면 저장된
     * 응답을 그대로 돌려주고 PG를 다시 호출하지 않는다.
     */
    @Authenticated
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @AuthPrincipal AuthenticatedPrincipal me,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank(message = "Idempotency-Key는 필수입니다.") String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {

        var outcome = idempotentRequestExecutor.execute(
                me.userId(), idempotencyKey, CHECKOUT_PATH, request,
                CheckoutResponse.class, HttpStatus.OK.value(),
                () -> CheckoutResponse.from(paymentCheckoutService.checkout(
                        me.userId(), request.orderId(), request.paymentKey(), request.amount())));

        // 영수증 주소가 담기므로 브라우저·중간 캐시에 남지 않게 한다.
        return ResponseEntity.status(outcome.status())
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("결제가 성공적으로 승인 및 완료되었습니다.", outcome.body()));
    }

    /** 결제 완료 결과 및 승인 영수증 조회. 없는 결제와 타인의 결제는 같은 404다. */
    @Authenticated
    @GetMapping("/{paymentId}/receipt")
    public ResponseEntity<ApiResponse<ReceiptResponse>> receipt(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable Long paymentId) {

        ReceiptResponse body = ReceiptResponse.from(
                paymentQueryService.getOwnedPayment(paymentId, me.userId()));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("주문 완료 및 결제 영수증 정보가 조회되었습니다.", body));
    }
}
