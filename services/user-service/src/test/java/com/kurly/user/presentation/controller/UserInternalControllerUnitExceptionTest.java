package com.kurly.user.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.user.application.DeliveryAddressService;
import com.kurly.user.exception.AddressNotFoundException;
import com.kurly.user.application.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserInternalControllerUnitExceptionTest {

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

    private void expectBadRequest(String body) throws Exception {
        mockMvc.perform(post("/internal/v1/users/sync-profile")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
    }

    @Nested
    @DisplayName("요청 검증")
    class ValidationTest {

        @Test
        void provider가_없으면_400이다() throws Exception {
            expectBadRequest("""
                    {"providerId":"1234567890"}""");
        }

        @Test
        void providerId가_비면_400이다() throws Exception {
            expectBadRequest("""
                    {"provider":"KAKAO","providerId":""}""");
        }

        @Test
        void 지원하지_않는_provider는_400이다() throws Exception {
            // enum에 없는 값은 본문 파싱 단계에서 걸린다. 내부 타입명이 응답에 새어나가면 안 된다.
            mockMvc.perform(post("/internal/v1/users/sync-profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"provider":"GOOGLE","providerId":"1234567890"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
        }

        @Test
        void 검증에_실패하면_동기화를_시도하지_않는다() throws Exception {
            expectBadRequest("{}");

            verify(userProfileService, never()).syncProfile(any(), any());
        }
    }

    @Nested
    @DisplayName("배송지 조회 실패")
    class DeliveryAddressFailureTest {

        private static final AuthenticatedPrincipal ME = new AuthenticatedPrincipal(10023L, Role.USER);

        @Test
        void 토큰_주체와_다른_회원을_조회하면_404다() throws Exception {
            // 검사하지 않으면 인증된 사용자가 memberId만 바꿔 타인의 주소·연락처를 읽는다.
            mockMvc.perform(get("/internal/v1/users/99999/delivery-addresses/8")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ADDRESS_NOT_FOUND"));
        }

        @Test
        void 타인_조회_시도는_서비스까지_가지_않는다() throws Exception {
            mockMvc.perform(get("/internal/v1/users/99999/delivery-addresses/8")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isNotFound());

            verify(deliveryAddressService, never()).findOwned(anyLong(), anyLong());
        }

        @Test
        void 없는_배송지와_타인의_배송지는_같은_404다() throws Exception {
            // 구분해 응답하면 id를 훑어 존재 여부를 알아낼 수 있다.
            given(deliveryAddressService.findOwned(10023L, 8L))
                    .willThrow(new AddressNotFoundException());

            mockMvc.perform(get("/internal/v1/users/10023/delivery-addresses/8")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ADDRESS_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 배송지입니다."));
        }
    }
}
