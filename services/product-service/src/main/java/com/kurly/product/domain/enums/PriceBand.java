package com.kurly.product.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum PriceBand {
    UNDER_5000("5,000원 이하", "0-5000", 0L, 5_000L),
    BETWEEN_5000_AND_10000("5,000원 ~ 10,000원", "5000-10000", 5_000L, 10_000L),
    BETWEEN_10000_AND_20000("10,000원 ~ 20,000원", "10000-20000", 10_000L, 20_000L),
    BETWEEN_20000_AND_30000("20,000원 ~ 30,000원", "20000-30000", 20_000L, 30_000L),
    OVER_30000("30,000원 이상", "30000-", 30_000L, Long.MAX_VALUE);

    private final String label;
    private final String value;
    private final long minInclusive;
    private final long maxExclusive;

    PriceBand(String label, String value, long minInclusive, long maxExclusive) {
        this.label = label;
        this.value = value;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
    }

    public boolean contains(long price) {
        return price >= minInclusive && price < maxExclusive;
    }

    @JsonCreator
    public static PriceBand from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PriceBand band : values()) {
            if (band.value.equalsIgnoreCase(value) || band.name().equalsIgnoreCase(value)) {
                return band;
            }
        }
        throw new IllegalArgumentException("Unknown price band: " + value);
    }
}
