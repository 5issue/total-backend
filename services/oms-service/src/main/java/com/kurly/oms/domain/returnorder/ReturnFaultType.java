package com.kurly.oms.domain.returnorder;

public enum ReturnFaultType {
    CUSTOMER, SELLER;

    public static ReturnFaultType from(String value) {
        for (ReturnFaultType type : values()) {
            if (type.name().equalsIgnoreCase(value)) return type;
        }
        throw new IllegalArgumentException("알 수 없는 WMS 귀책 유형: " + value);
    }
}
