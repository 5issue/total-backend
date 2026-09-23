package com.kurly.oms.application;

import com.kurly.oms.domain.fulfillment.*;
import com.kurly.oms.presentation.dto.DeliveryPromiseRequest;
import com.kurly.oms.presentation.dto.DeliveryPromiseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OmsFulfillmentServiceUnitTest {

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
    @DisplayName("배송 가능 약속 조회 (getDeliveryPromises)")
    class GetDeliveryPromisesTest {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 주소가_null이거나_공백이면_PARCEL_NATIONWIDE_권역으로_파싱한다(String address) {
            // given
            DeliveryPromiseRequest request = createRequest(address);
            TamRegion region = TamRegion.create("PARCEL_NATIONWIDE", "전국 택배 권역", DeliveryType.PARCEL);
            DeliverySlot slot = DeliverySlot.create(1L, "SLOT_PARCEL", "택배 1회차", LocalTime.of(18, 0), LocalTime.of(20, 0), LocalTime.of(9, 0), LocalTime.of(18, 0), 2);

            when(tamRegionRepository.findByRegionCodeAndStatus("PARCEL_NATIONWIDE", RegionStatus.ACTIVE))
                    .thenReturn(Optional.of(region));
            when(deliverySlotRepository.findByRegionIdAndIsActiveTrue(region.getId()))
                    .thenReturn(List.of(slot));

            // when
            DeliveryPromiseResponse response = omsFulfillmentService.getDeliveryPromises(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.deliverable()).isTrue();
            assertThat(response.deliveryType()).isEqualTo(DeliveryType.PARCEL);
            verify(tamRegionRepository).findByRegionCodeAndStatus("PARCEL_NATIONWIDE", RegionStatus.ACTIVE);
        }

        @ParameterizedTest
        @CsvSource({
                "'서울특별시 강남구 테헤란로 111', 'DAWN_SEOUL_METRO', 'DAWN'",
                "'경기도 성남시 분당구 판교역로', 'DAWN_SEOUL_METRO', 'DAWN'",
                "'인천광역시 부평구 부평대로', 'DAWN_SEOUL_METRO', 'DAWN'",
                "'부산광역시 해운대구 센텀동로', 'DAWN_BUSAN', 'DAWN'",
                "'대구광역시 수성구 달구벌대로', 'DAWN_DAEGU', 'DAWN'",
                "'대전광역시 서구 둔산로', 'DAWN_DAEJEON', 'DAWN'",
                "'광주광역시 서구 상무번영로', 'DAWN_GWANGJU', 'DAWN'",
                "'울산광역시 남구 삼산로', 'DAWN_ULSAN', 'DAWN'",
                "'강원도 춘천시 중앙로', 'PARCEL_NATIONWIDE', 'PARCEL'",
                "'제주특별자치도 제주시 첨단로', 'PARCEL_NATIONWIDE', 'PARCEL'"
        })
        void 주소_키워드_조건_분기를_망라하여_올바른_권역_코드로_파싱한다(String address, String expectedRegionCode, String expectedDeliveryTypeStr) {
            // given
            DeliveryPromiseRequest request = createRequest(address);
            DeliveryType deliveryType = DeliveryType.valueOf(expectedDeliveryTypeStr);
            TamRegion region = TamRegion.create(expectedRegionCode, expectedRegionCode + " 권역", deliveryType);
            DeliverySlot slot = DeliverySlot.create(1L, "SLOT_01", "1회차", LocalTime.of(23, 0), LocalTime.of(0, 0), LocalTime.of(1, 0), LocalTime.of(7, 0), 1);

            when(tamRegionRepository.findByRegionCodeAndStatus(expectedRegionCode, RegionStatus.ACTIVE))
                    .thenReturn(Optional.of(region));
            when(deliverySlotRepository.findByRegionIdAndIsActiveTrue(region.getId()))
                    .thenReturn(List.of(slot));

            // when
            DeliveryPromiseResponse response = omsFulfillmentService.getDeliveryPromises(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.deliverable()).isTrue();
            assertThat(response.deliveryType()).isEqualTo(deliveryType);
            verify(tamRegionRepository).findByRegionCodeAndStatus(expectedRegionCode, RegionStatus.ACTIVE);
        }

        @Test
        void 현재_시간이_CutoffTime_전이면_leadDays만_적용하여_예상_배송일을_계산한다() {
            // given
            DeliveryPromiseRequest request = createRequest("서울특별시 강남구 테헤란로 111");
            TamRegion region = TamRegion.create("DAWN_SEOUL_METRO", "수도권 권역", DeliveryType.DAWN);
            DeliverySlot slot = DeliverySlot.create(1L, "SLOT_01", "새벽 1회차", LocalTime.of(23, 59, 59), LocalTime.of(0, 0), LocalTime.of(1, 0), LocalTime.of(7, 0), 1);

            when(tamRegionRepository.findByRegionCodeAndStatus("DAWN_SEOUL_METRO", RegionStatus.ACTIVE))
                    .thenReturn(Optional.of(region));
            when(deliverySlotRepository.findByRegionIdAndIsActiveTrue(region.getId()))
                    .thenReturn(List.of(slot));

            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            Instant expectedCutoffAt = slot.getCutoffTime().atDate(today).toInstant(ZoneOffset.UTC);
            Instant expectedDeliveryAt = slot.getDeliveryEndTime().atDate(today.plusDays(1)).toInstant(ZoneOffset.UTC);

            // when
            DeliveryPromiseResponse response = omsFulfillmentService.getDeliveryPromises(request);

            // then
            assertThat(response.cutoffAt()).isEqualTo(expectedCutoffAt);
            assertThat(response.expectedDeliveryAt()).isEqualTo(expectedDeliveryAt);
        }

        @Test
        void 현재_시간이_CutoffTime_후이면_leadDays에_1일을_추가하여_예상_배송일을_계산한다() {
            // given
            DeliveryPromiseRequest request = createRequest("서울특별시 강남구 테헤란로 111");
            TamRegion region = TamRegion.create("DAWN_SEOUL_METRO", "수도권 권역", DeliveryType.DAWN);
            DeliverySlot slot = DeliverySlot.create(1L, "SLOT_01", "새벽 1회차", LocalTime.of(0, 0, 0), LocalTime.of(0, 0), LocalTime.of(1, 0), LocalTime.of(7, 0), 1);

            when(tamRegionRepository.findByRegionCodeAndStatus("DAWN_SEOUL_METRO", RegionStatus.ACTIVE))
                    .thenReturn(Optional.of(region));
            when(deliverySlotRepository.findByRegionIdAndIsActiveTrue(region.getId()))
                    .thenReturn(List.of(slot));

            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            Instant expectedCutoffAt = slot.getCutoffTime().atDate(today).toInstant(ZoneOffset.UTC);
            Instant expectedDeliveryAt = slot.getDeliveryEndTime().atDate(today.plusDays(1 + 1)).toInstant(ZoneOffset.UTC);

            // when
            DeliveryPromiseResponse response = omsFulfillmentService.getDeliveryPromises(request);

            // then
            assertThat(response.cutoffAt()).isEqualTo(expectedCutoffAt);
            assertThat(response.expectedDeliveryAt()).isEqualTo(expectedDeliveryAt);
        }
    }
}