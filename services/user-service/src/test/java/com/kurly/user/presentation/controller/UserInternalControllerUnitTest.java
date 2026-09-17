package com.kurly.user.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.user.application.DeliveryAddressService;
import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.application.UserProfileService;
import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.enums.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserInternalControllerUnitTest {

    private static final String BODY = """
            {"provider":"KAKAO","providerId":"1234567890"}""";

    @Mock UserProfileService userProfileService;
    @Mock DeliveryAddressService deliveryAddressService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserInternalController(userProfileService, deliveryAddressService))
                // 인터셉터가 요청 attribute에 담아둔 주체를 파라미터로 주입하는 실제 리졸버를 쓴다.
                .setCustomArgumentResolvers(new AuthPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static User user(Long id) {
        User u = User.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("1234567890")
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Nested
    @DisplayName("프로필 동기화")
    class SyncProfileTest {

        @Test
        void 신규_회원이면_201이다() throws Exception {
            given(userProfileService.syncProfile(AuthProvider.KAKAO, "1234567890"))
                    .willReturn(new UserProfileService.SyncResult(user(10023L), true));

            mockMvc.perform(post("/internal/v1/users/sync-profile")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("회원 프로필 동기화가 완료되었습니다."))
                    .andExpect(jsonPath("$.data.userId").value(10023))
                    .andExpect(jsonPath("$.data.isNewUser").value(true));
        }

        @Test
        void 기존_회원이면_200이다() throws Exception {
            given(userProfileService.syncProfile(AuthProvider.KAKAO, "1234567890"))
                    .willReturn(new UserProfileService.SyncResult(user(10023L), false));

            mockMvc.perform(post("/internal/v1/users/sync-profile")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isNewUser").value(false));
        }
    }

    @Nested
    @DisplayName("배송지 조회 — order 서비스 연동")
    class DeliveryAddressTest {

        private static final AuthenticatedPrincipal ME = new AuthenticatedPrincipal(10023L, Role.USER);

        private static DeliveryAddress address(Long id) {
            DeliveryAddress a = DeliveryAddress.builder()
                    .userId(10023L)
                    .addressName("우리집")
                    .recipientName("홍길동")
                    .phone("010-1234-5678")
                    .zipCode("06234")
                    .address("서울특별시 강남구 테헤란로 123")
                    .addressDetail("101동 1001호")
                    .defaultAddress(true)
                    .build();
            ReflectionTestUtils.setField(a, "id", id);
            return a;
        }

        @Test
        void 합의한_필드_이름으로_응답한다() throws Exception {
            // 엔티티의 phone·addressDetail이 계약에서는 recipientPhone·detailAddress다.
            // 이름이 어긋나면 호출측 역직렬화가 깨진다.
            given(deliveryAddressService.findOwned(10023L, 8L)).willReturn(address(8L));

            mockMvc.perform(get("/internal/v1/users/10023/delivery-addresses/8")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("회원 배송지 조회가 완료되었습니다."))
                    .andExpect(jsonPath("$.data.addressId").value(8))
                    .andExpect(jsonPath("$.data.addressName").value("우리집"))
                    .andExpect(jsonPath("$.data.recipientName").value("홍길동"))
                    .andExpect(jsonPath("$.data.recipientPhone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.data.address").value("서울특별시 강남구 테헤란로 123"))
                    .andExpect(jsonPath("$.data.detailAddress").value("101동 1001호"));
        }

        @Test
        void 합의에_없는_필드는_싣지_않는다() throws Exception {
            // 우편번호는 명세에 없다. 임의로 더하면 호출측 역직렬화 설정에 따라 깨질 수 있다.
            given(deliveryAddressService.findOwned(10023L, 8L)).willReturn(address(8L));

            mockMvc.perform(get("/internal/v1/users/10023/delivery-addresses/8")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(jsonPath("$.data.zipCode").doesNotExist());
        }
    }
}
