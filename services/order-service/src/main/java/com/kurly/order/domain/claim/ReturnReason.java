package com.kurly.order.domain.claim;

import java.util.Arrays;
import java.util.Optional;

public enum ReturnReason {
    RTN01("단순 변심", false),
    RTN02("상품 불량", true),
    RTN03("상품 파손", true),
    RTN04("냉해·해동", true),
    RTN05("오배송", true),
    RTN06("상품 누락", true),
    RTN07("상품 품절", false),
    RTN08("상품정보 상이", true);

    private final String description;
    private final boolean evidenceRequired;

    ReturnReason(String description, boolean evidenceRequired) {
        this.description = description;
        this.evidenceRequired = evidenceRequired;
    }

    public String description() {
        return description;
    }

    public boolean evidenceRequired() {
        return evidenceRequired;
    }

    public static Optional<ReturnReason> find(String code) {
        return Arrays.stream(values()).filter(reason -> reason.name().equals(code)).findFirst();
    }
}
