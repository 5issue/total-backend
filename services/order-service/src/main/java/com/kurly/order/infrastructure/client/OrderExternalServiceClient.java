package com.kurly.order.infrastructure.client;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.response.ApiResponse;
import com.kurly.order.application.CartExternalService;
import com.kurly.order.application.OrderExternalService;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.common.StorageType;
import com.kurly.order.infrastructure.dto.*;
import com.kurly.order.presentation.dto.CartResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class OrderExternalServiceClient implements OrderExternalService, CartExternalService {

    private final RestClient omsClient;
    private final RestClient paymentClient;
    private final RestClient productClient;
    private final RestClient memberClient;

    public OrderExternalServiceClient(
            @Value("${services.oms.base-url:http://localhost:8086}") String omsBaseUrl,
            @Value("${services.payment.base-url:http://localhost:8083}") String paymentBaseUrl,
            @Value("${services.product.base-url:http://localhost:8084}") String productBaseUrl,
            @Value("${services.member.base-url:http://localhost:8088}") String memberBaseUrl,
            @Value("${services.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${services.http.read-timeout:5s}") Duration readTimeout
    ) {
        this.omsClient = securedClient(omsBaseUrl, connectTimeout, readTimeout);
        this.paymentClient = securedClient(paymentBaseUrl, connectTimeout, readTimeout);
        this.productClient = securedClient(productBaseUrl, connectTimeout, readTimeout);
        this.memberClient = securedClient(memberBaseUrl, connectTimeout, readTimeout);
    }

    @Override
    public void holdInventory(UUID reservationToken, List<CartItem> items) {
        var request = new InventoryHoldRequest(
                reservationToken,
                items.stream().map(item ->
                        new InventoryHoldRequest.InventoryHoldItem(
                                item.getProductId(),
                                item.getQuantity())
                ).toList()
        );

        ApiResponse<Void> response = productClient.post()
                .uri("/internal/v1/products/inventory/hold")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        if (response == null) {
            throw new IllegalStateException("상품 서비스의 재고 선점 처리에 실패했습니다.");
        }
    }

    @Override
    public void releaseInventory(UUID reservationToken) {
        productClient.post()
                .uri("/internal/v1/products/inventory/release")
                .body(new InventoryReleaseRequest(reservationToken))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public CancelEligibilityResponse getCancelEligibility(Long orderId) {
        ApiResponse<CancelEligibilityResponse> response = omsClient.get()
                .uri("/internal/v1/oms/orders/{orderId}/cancel-eligibility", orderId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<CancelEligibilityResponse>>() {
                });

        if (response == null || response.getData() == null) {
            throw new BusinessException(OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE, "OMS 서비스 응답이 올바르지 않습니다.");
        }

        return response.getData();
    }

    @Override
    public void cancelPayment(Long paymentId, String idempotencyKey, String cancelReason) {
        paymentClient.post()
                .uri("/internal/v1/payments/{paymentId}/cancel", paymentId)
                .header("Idempotency-Key", idempotencyKey)
                .body(new CancelPaymentRequest(cancelReason))
                .retrieve()
                .toBodilessEntity();
    }

    public AddressResponse getAddress(Long memberId, Long addressId) {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String authHeader = attributes != null
                ? attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION)
                : null;

        try {
            ApiResponse<AddressResponse> response = memberClient.get()
                    .uri("/internal/v1/users/{memberId}/delivery-addresses/{addressId}", memberId, addressId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<AddressResponse>>() {
                    });
            return response != null ? response.getData() : null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @Override
    public List<CartProductInfo> getProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        ProductApiResponse response = productClient.post()
                .uri("/internal/v1/products/batch-summary")
                .body(new ProductRequest(productIds))
                .retrieve()
                .body(ProductApiResponse.class);

        if (response == null || response.data() == null || response.data().products() == null) {
            throw new IllegalStateException("상품 서비스의 상품 응답이 비어 있습니다.");
        }

        return response.data().products().stream()
                .map(item -> new CartProductInfo(
                        item.productId(),
                        item.name(),
                        item.salePrice(),
                        item.thumbnailUrl(),
                        item.storageType(),
                        item.status(),
                        item.seller(),
                        item.inventory() != null ? new CartProductInfo.InventoryInfo(
                                item.inventory().availableQuantity(),
                                item.inventory().isSoldOut(),
                                item.inventory().maxQuantityPerOrder()
                        ) : null
                ))
                .toList();
    }

    @Override
    public DeliveryAddressResponseDto.Promise getDeliveryPromise(AddressResponse address) {
        try {
            PromiseApiResponse response = omsClient.post().uri("/internal/v1/oms/delivery-promises")
                    .body(address).retrieve().body(PromiseApiResponse.class);
            if (response == null || response.data() == null) {
                throw new IllegalStateException("OMS의 배송 가능 여부 응답이 비어 있습니다.");
            }
            return response.data();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private RestClient securedClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(HttpHeaders.ACCEPT_CHARSET, "utf-8");

                    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributes != null) {
                        HttpServletRequest currentRequest = attributes.getRequest();
                        String authHeader = currentRequest.getHeader(HttpHeaders.AUTHORIZATION);

                        if (StringUtils.hasText(authHeader)) {
                            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);
                        }
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    private record CancelPaymentRequest(String cancelReason) {
    }

    private record InventoryReleaseRequest(UUID reservationToken) {
    }

    private record ProductRequest(List<Long> productIds) {
    }

    private record ProductApiResponse(ProductData data) {
    }

    private record ProductData(List<ProductSummary> products) {
    }

    private record ProductSummary(Long productId, String name, Long salePrice, String thumbnailUrl,
                                  StorageType storageType, String status, String seller,
                                  InventoryInfo inventory) {
        private CartResponseDto.Product toProduct() {
            return new CartResponseDto.Product(productId, productId, name, thumbnailUrl, salePrice,
                    inventory.maxQuantityPerOrder(), !inventory.isSoldOut() && inventory.availableQuantity() > 0,
                    null, storageType, null, seller, 0L);
        }
    }

    private record InventoryInfo(int availableQuantity, boolean isSoldOut, int maxQuantityPerOrder) {
    }

    private record PromiseApiResponse(DeliveryAddressResponseDto.Promise data) {
    }
}
