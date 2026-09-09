package com.kurly.user.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.user.application.DeliveryAddressService;
import com.kurly.user.application.UserProfileService;
import com.kurly.user.exception.AddressNotFoundException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerUnitExceptionTest {

    private static final AuthenticatedPrincipal ME = new AuthenticatedPrincipal(1L, Role.USER);

    @Mock UserProfileService userProfileService;
    @Mock DeliveryAddressService deliveryAddressService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userProfileService, deliveryAddressService))
                .setCustomArgumentResolvers(new AuthPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("인증 주체 없음")
    class PrincipalTest {

        @Test
        void 주체가_주입되지_않으면_401이다() throws Exception {
            // 인터셉터를 통과하지 않은 요청이다. 설정 실수를 통과시키지 않는다.
            mockMvc.perform(get("/api/v1/users/me/profile"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("기본 배송지 변경 실패")
    class SetDefaultTest {

        @Test
        void 없는_배송지는_404_ADDRESS_NOT_FOUND다() throws Exception {
            willThrow(new AddressNotFoundException())
                    .given(deliveryAddressService).setDefault(anyLong(), anyLong());

            mockMvc.perform(patch("/api/v1/users/me/addresses/99999/default")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ADDRESS_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 배송지입니다."));
        }

        @Test
        void 타인의_배송지도_403이_아니라_404다() throws Exception {
            // 403으로 구분하면 ID를 훑어 남의 배송지 존재 여부를 알아낼 수 있다.
            willThrow(new AddressNotFoundException())
                    .given(deliveryAddressService).setDefault(anyLong(), anyLong());

            mockMvc.perform(patch("/api/v1/users/me/addresses/3/default")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ADDRESS_NOT_FOUND"));
        }

        @Test
        void 숫자가_아닌_배송지_ID는_400이다() throws Exception {
            // 처리기가 없으면 클라이언트 잘못인데도 500이 나간다.
            mockMvc.perform(patch("/api/v1/users/me/addresses/abc/default")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
        }
    }

    @Nested
    @DisplayName("배송지 등록 검증")
    class CreateAddressTest {

        private void expectInvalid(String body, String expectedField) throws Exception {
            mockMvc.perform(post("/api/v1/users/me/addresses")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString(expectedField)));
        }

        @Test
        void 배송지_이름이_비면_400이다() throws Exception {
            expectInvalid("""
                    {"addressName":"","recipientName":"홍길동","phone":"010-1234-5678",
                     "zipCode":"06234","address":"서울시 강남구"}""", "addressName");
        }

        @Test
        void 수취인이_없으면_400이다() throws Exception {
            expectInvalid("""
                    {"addressName":"집","phone":"010-1234-5678",
                     "zipCode":"06234","address":"서울시 강남구"}""", "recipientName");
        }

        @Test
        void 연락처에_문자가_섞이면_400이다() throws Exception {
            expectInvalid("""
                    {"addressName":"집","recipientName":"홍길동","phone":"010-abcd-5678",
                     "zipCode":"06234","address":"서울시 강남구"}""", "phone");
        }

        @Test
        void 우편번호가_5자리가_아니면_400이다() throws Exception {
            expectInvalid("""
                    {"addressName":"집","recipientName":"홍길동","phone":"010-1234-5678",
                     "zipCode":"123","address":"서울시 강남구"}""", "zipCode");
        }

        @Test
        void 주소가_비면_400이다() throws Exception {
            expectInvalid("""
                    {"addressName":"집","recipientName":"홍길동","phone":"010-1234-5678",
                     "zipCode":"06234","address":""}""", "address");
        }

        @Test
        void 배송지_이름이_50자를_넘으면_400이다() throws Exception {
            expectInvalid("""
                    {"addressName":"%s","recipientName":"홍길동","phone":"010-1234-5678",
                     "zipCode":"06234","address":"서울시 강남구"}"""
                    .formatted("가".repeat(51)), "addressName");
        }

        @Test
        void 본문이_JSON이_아니면_400이다() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/addresses")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"))
                    // 파싱 실패 상세에는 내부 타입명이 담기므로 응답에 싣지 않는다.
                    .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
        }

        @Test
        void 검증에_실패하면_서비스를_호출하지_않는다() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/addresses")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"addressName":"","recipientName":"","phone":"","zipCode":"","address":""}"""))
                    .andExpect(status().isBadRequest());

            org.mockito.Mockito.verify(deliveryAddressService, org.mockito.Mockito.never())
                    .create(any(), any());
        }
    }

    @Nested
    @DisplayName("프로필 조회 실패")
    class ProfileTest {

        @Test
        void 주체가_사라졌으면_401이다() throws Exception {
            given(userProfileService.getProfile(1L))
                    .willThrow(new com.kurly.common.exception.UnauthorizedException("인증이 필요합니다."));

            mockMvc.perform(get("/api/v1/users/me/profile")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }
}
