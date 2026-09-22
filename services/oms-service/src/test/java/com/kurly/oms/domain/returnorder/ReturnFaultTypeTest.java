package com.kurly.oms.domain.returnorder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ReturnFaultTypeTest {
    @Test
    void knownValuesAreCaseInsensitiveAndUnknownValuesFail() {
        assertThat(ReturnFaultType.from("customer")).isEqualTo(ReturnFaultType.CUSTOMER);
        assertThat(ReturnFaultType.from("SELLER")).isEqualTo(ReturnFaultType.SELLER);
        assertThatThrownBy(() -> ReturnFaultType.from("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
