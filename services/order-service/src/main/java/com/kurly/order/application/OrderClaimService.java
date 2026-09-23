package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.order.domain.claim.OrderClaim;
import com.kurly.order.domain.claim.OrderClaimRepository;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.order.OrderItem;
import com.kurly.order.infrastructure.repository.OrderClaimJpaRepository;
import com.kurly.order.presentation.dto.ReturnDetailResponse;
import com.kurly.order.presentation.dto.ReturnListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class OrderClaimService {

    private final OrderClaimRepository orderClaimRepository;
    private final OrderClaimJpaRepository orderClaimJpaRepository;

    @Transactional(readOnly = true)
    public ReturnListResponse listReturns(String status, List<String> storageTypes, Pageable pageable) {
        boolean filterStorage = storageTypes != null && !storageTypes.isEmpty();
        Page<OrderClaim> claims = orderClaimJpaRepository.searchReturns(
                status, filterStorage, filterStorage ? storageTypes : List.of("ROOM_TEMPERATURE"), pageable);
        if (!claims.isEmpty()) {
            orderClaimJpaRepository.findOrdersWithItems(claims.getContent().stream()
                    .map(claim -> claim.getOrder().getId()).toList());
        }

        List<ReturnListResponse.ReturnSummary> items = claims.getContent().stream()
                .map(claim -> new ReturnListResponse.ReturnSummary(
                        claim.getId(),
                        claim.getOrder().getId(),
                        claim.getOrder().getOrderNo(),
                        claim.getOrder().getMemberId(),
                        claim.getOrder().getItems().stream().map(OrderItem::getStorageType)
                                .distinct().sorted(Comparator.reverseOrder()).map(Enum::name).toList(),
                        claim.getReasonCode(),
                        claim.getStatus().name(),
                        claim.getRequestedAt()
                ))
                .toList();

        return new ReturnListResponse(
                claims.getTotalElements(),
                claims.getNumber() + 1,
                claims.getSize(),
                items
        );
    }

    @Transactional(readOnly = true)
    public ReturnDetailResponse getReturnDetail(Long returnId) {
        OrderClaim claim = orderClaimRepository.findByIdWithAttachments(returnId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_CLAIM));

        return ReturnDetailResponse.from(claim);
    }

}
