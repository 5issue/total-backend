package com.kurly.order.domain.claim;

import java.util.Arrays;
import java.util.Optional;

public enum CancelReason {
    CNL01("단순 변심", false),
    CNL02("주문 실수", false),
    CNL03("배송지 변경", false),
    CNL04("결제 수단 변경", false),
    CNL05("배송 지연", false),
    CNL99("기타", true);

    private final String description;
    private final boolean detailRequired;

    CancelReason(String description, boolean detailRequired) {
        this.description = description;
        this.detailRequired = detailRequired;
    }

    public String description() {
        return description;
    }

    public boolean detailRequired() {
        return detailRequired;
    }

    public static Optional<CancelReason> find(String code) {
        return Arrays.stream(values()).filter(reason -> reason.name().equals(code)).findFirst();
    }
}
