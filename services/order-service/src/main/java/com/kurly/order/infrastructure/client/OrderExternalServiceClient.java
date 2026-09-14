package com.kurly.order.infrastructure.client;

import com.kurly.common.response.ApiResponse;
import com.kurly.order.application.CartExternalService;
import com.kurly.order.application.OrderExternalService;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.CheckoutInventoryResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import jakarta.servlet.http.HttpServletRequest;
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

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Component
public class OrderExternalServiceClient implements OrderExternalService, CartExternalService {

    private final RestClient omsClient;
    private final RestClient paymentClient;
    private final RestClient productClient;
    private final RestClient memberClient;

    public OrderExternalServiceClient(
            @Value("${services.oms.base-url:http://localhost:8085}") String omsBaseUrl,
            @Value("${services.payment.base-url:http://localhost:8083}") String paymentBaseUrl,
            @Value("${services.product.base-url:http://localhost:8081}") String productBaseUrl,
            @Value("${services.member.base-url:http://localhost:8080}") String memberBaseUrl,
            @Value("${services.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${services.http.read-timeout:5s}") Duration readTimeout
    ) {
        this.omsClient = securedClient(omsBaseUrl, connectTimeout, readTimeout);
        this.paymentClient = securedClient(paymentBaseUrl, connectTimeout, readTimeout);
        this.productClient = securedClient(productBaseUrl, connectTimeout, readTimeout);
        this.memberClient = securedClient(memberBaseUrl, connectTimeout, readTimeout);
    }

    @Override
    public CheckoutInventoryResponseDto holdInventory(String reservationToken, List<CartItem> items) {
        var request = new InventoryHoldRequest(
                reservationToken,
                items.stream().map(item -> new InventoryHoldItem(item.getProductId(), item.getQuantity())).toList()
        );

        InventoryHoldApiResponse response = productClient.post()
                .uri("/internal/v1/products/inventory/hold")
                .body(request)
                .retrieve()
                .body(InventoryHoldApiResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("상품 서비스의 재고 선점 응답이 비어 있습니다.");
        }
        return response.data();
    }

    @Override
    public void releaseInventory(String reservationToken) {
        productClient.post()
                .uri("/internal/v1/products/inventory/release")
                .body(new InventoryReleaseRequest(reservationToken))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean isCancellationEligible(Long orderId) {
        CancelEligibilityResponse response = omsClient.get()
                .uri("/internal/v1/oms/orders/{orderId}/cancel-eligibility", orderId)
                .retrieve()
                .body(CancelEligibilityResponse.class);
        return response != null && response.data() != null && "CANCELLABLE".equals(response.data().status());
    }

    @Override
    public void cancelPayment(Long paymentId) {
        paymentClient.post()
                .uri("/internal/v1/payments/{paymentId}/cancel", paymentId)
                .retrieve()
                .toBodilessEntity();
    }

    public CartResponseDto.Address getAddress(Long memberId, Long addressId) {
        try {
            ApiResponse<CartResponseDto.Address> response = memberClient.get()
                    .uri("/internal/v1/users/{memberId}/delivery-addresses/{addressId}", memberId, addressId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null ? response.getData() : null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @Override
    public List<CartResponseDto.Product> getProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        ProductApiResponse response = productClient.post().uri("/internal/v1/products/batch-summary")
                .body(new ProductRequest(productIds)).retrieve().body(ProductApiResponse.class);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("상품 서비스의 상품 응답이 비어 있습니다.");
        }
        return response.data();
    }

    @Override
    public DeliveryAddressResponseDto.Promise getDeliveryPromise(CartResponseDto.Address address) {
        PromiseApiResponse response = omsClient.post().uri("/internal/v1/oms/delivery-promise")
                .body(address).retrieve().body(PromiseApiResponse.class);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("OMS의 배송 가능 여부 응답이 비어 있습니다.");
        }
        return response.data();
    }

    private RestClient securedClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributes != null) {
                        HttpServletRequest currentRequest = attributes.getRequest();
                        String authHeader = currentRequest.getHeader(HttpHeaders.AUTHORIZATION);

                        URI uri = request.getURI();
                        if (StringUtils.hasText(authHeader) && "https".equalsIgnoreCase(uri.getScheme())) {
                            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);
                        }
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    private record CancelEligibility(String status) {
    }

    private record CancelEligibilityResponse(CancelEligibility data) {
    }

    private record InventoryHoldRequest(String reservationToken, List<InventoryHoldItem> items) {
    }

    private record InventoryHoldItem(Long productId, Integer quantity) {
    }

    private record InventoryReleaseRequest(String reservationToken) {
    }

    private record InventoryHoldApiResponse(CheckoutInventoryResponseDto data) {
    }

    private record AddressApiResponse(CartResponseDto.Address data) {
    }

    private record ProductRequest(List<Long> productIds) {
    }

    private record ProductApiResponse(List<CartResponseDto.Product> data) {
    }

    private record PromiseApiResponse(DeliveryAddressResponseDto.Promise data) {
    }
}
