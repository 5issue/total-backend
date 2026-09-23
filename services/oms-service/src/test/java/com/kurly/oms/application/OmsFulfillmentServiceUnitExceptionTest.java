package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.fulfillment.DeliverySlotRepository;
import com.kurly.oms.domain.fulfillment.RegionStatus;
import com.kurly.oms.domain.fulfillment.TamRegion;
import com.kurly.oms.domain.fulfillment.TamRegionRepository;
import com.kurly.oms.presentation.dto.DeliveryPromiseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OmsFulfillmentServiceUnitExceptionTest {

    @Mock
    private TamRegionRepository tamRegionRepository;

    @Mock
    private DeliverySlotRepository deliverySlotRepository;

    @InjectMocks
    private OmsFulfillmentService omsFulfillmentService;

    private DeliveryPromiseRequest createRequest(String address) {
        return new DeliveryPromiseRequest(
                2001L,
                "우리집",
                "홍길동",
                "010-1234-5678",
                address,
                "101동 1001호"
        );
    }

    @Nested
    @DisplayName("배송 가능 약속 조회 예외 (getDeliveryPromises)")
    class GetDeliveryPromisesExceptionTest {

        @Test
        void 활성화된_권역_정보가_존재하지_않으면_ORD_NOT_FOUND_ADDRESS_예외가_발생한다() {
            // given
            DeliveryPromiseRequest request = createRequest("서울특별시 강남구 테헤란로 111");
            when(tamRegionRepository.findByRegionCodeAndStatus(anyString(), any(RegionStatus.class)))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsFulfillmentService.getDeliveryPromises(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.ORD_NOT_FOUND_ADDRESS);
        }

        @Test
        void 활성화된_배송_슬롯이_존재하지_않으면_ORD_NOT_FOUND_ADDRESS_예외가_발생한다() {
            // given
            DeliveryPromiseRequest request = createRequest("서울특별시 강남구 테헤란로 111");
            TamRegion region = TamRegion.create("DAWN_SEOUL_METRO", "수도권 권역", com.kurly.oms.domain.fulfillment.DeliveryType.DAWN);

            when(tamRegionRepository.findByRegionCodeAndStatus(anyString(), any(RegionStatus.class)))
                    .thenReturn(Optional.of(region));
            when(deliverySlotRepository.findByRegionIdAndIsActiveTrue(region.getId()))
                    .thenReturn(List.of());

            // when & then
            assertThatThrownBy(() -> omsFulfillmentService.getDeliveryPromises(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.ORD_NOT_FOUND_ADDRESS);
        }
    }
}