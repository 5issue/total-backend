package com.kurly.user.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.user.application.DeliveryAddressService;
import com.kurly.user.application.UserProfileService;
import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.presentation.dto.DefaultAddressResponse;
import com.kurly.user.presentation.dto.UserProfileResponse;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerUnitTest {

    private static final AuthenticatedPrincipal ME = new AuthenticatedPrincipal(1L, Role.USER);

    private static final String CREATE_BODY = """
            {"addressName":"회사","recipientName":"홍길동","phone":"010-1234-5678",
             "zipCode":"06234","address":"서울시 강남구 테헤란로 123","addressDetail":"101동 201호",
             "isDefault":true,"accessMethod":"공동현관 비밀번호 (1234#)"}""";

    @Mock UserProfileService userProfileService;
    @Mock DeliveryAddressService deliveryAddressService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userProfileService, deliveryAddressService))
                // 인터셉터가 요청 attribute에 담아둔 주체를 파라미터로 주입하는 실제 리졸버를 쓴다.
                .setCustomArgumentResolvers(new AuthPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static DeliveryAddress address(Long id, String name, boolean isDefault) {
        DeliveryAddress a = DeliveryAddress.builder()
                .userId(1L)
                .addressName(name)
                .recipientName("홍길동")
                .phone("010-1234-5678")
                .zipCode("06234")
                .address("서울시 강남구 테헤란로 123")
                .addressDetail("101동 202호")
                .defaultAddress(isDefault)
                .accessMethod("공동현관 비밀번호 (1234#)")
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Nested
    @DisplayName("GET /profile")
    class ProfileTest {

        @Test
        void 주문자_정보와_기본_배송지를_내려준다() throws Exception {
            given(userProfileService.getProfile(1L)).willReturn(new UserProfileResponse(
                    "홍길동", DefaultAddressResponse.from(address(105L, "우리집", true))));

            mockMvc.perform(get("/api/v1/users/me/profile")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("주문 프로필 정보가 조회되었습니다."))
                    .andExpect(jsonPath("$.data.name").value("홍길동"))
                    .andExpect(jsonPath("$.data.defaultAddress.addressId").value(105))
                    .andExpect(jsonPath("$.data.defaultAddress.addressDetail").value("101동 202호"))
                    // 주문서에 쓰지 않는 개인정보는 담지 않는다.
                    .andExpect(jsonPath("$.data.defaultAddress.phone").doesNotExist())
                    .andExpect(jsonPath("$.data.defaultAddress.isDefault").doesNotExist());
        }

        @Test
        void 기본_배송지가_없으면_null이다() throws Exception {
            given(userProfileService.getProfile(1L)).willReturn(new UserProfileResponse("홍길동", null));

            mockMvc.perform(get("/api/v1/users/me/profile")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("홍길동"))
                    .andExpect(jsonPath("$.data.defaultAddress").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Nested
    @DisplayName("GET /addresses")
    class AddressListTest {

        @Test
        void 목록을_내려준다() throws Exception {
            given(deliveryAddressService.findAll(1L))
                    .willReturn(List.of(address(105L, "우리집", true), address(120L, "회사", false)));

            mockMvc.perform(get("/api/v1/users/me/addresses")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("배송지 목록 조회가 완료되었습니다."))
                    .andExpect(jsonPath("$.data.addresses.length()").value(2))
                    .andExpect(jsonPath("$.data.addresses[0].addressId").value(105))
                    .andExpect(jsonPath("$.data.addresses[0].isDefault").value(true))
                    .andExpect(jsonPath("$.data.addresses[0].phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.data.addresses[1].isDefault").value(false));
        }

        @Test
        void 배송지가_없으면_빈_배열이다() throws Exception {
            given(deliveryAddressService.findAll(1L)).willReturn(List.of());

            mockMvc.perform(get("/api/v1/users/me/addresses")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.addresses").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /addresses")
    class CreateAddressTest {

        @Test
        void 등록하면_201과_배송지_ID를_돌려준다() throws Exception {
            given(deliveryAddressService.create(eq(1L), any())).willReturn(address(110L, "회사", true));

            mockMvc.perform(post("/api/v1/users/me/addresses")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("신규 배송지가 등록되었습니다."))
                    .andExpect(jsonPath("$.data.addressId").value(110))
                    .andExpect(jsonPath("$.data.success").value(true));
        }

        @Test
        void 선택_필드는_생략할_수_있다() throws Exception {
            given(deliveryAddressService.create(eq(1L), any())).willReturn(address(111L, "기숙사", false));

            mockMvc.perform(post("/api/v1/users/me/addresses")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"addressName":"기숙사","recipientName":"홍길동","phone":"010-9999-8888",
                                     "zipCode":"54321","address":"대전시 유성구"}"""))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("PATCH /addresses/{addressId}/default")
    class SetDefaultTest {

        @Test
        void 기본_배송지를_변경한다() throws Exception {
            given(deliveryAddressService.setDefault(1L, 110L)).willReturn(address(110L, "회사", true));

            mockMvc.perform(patch("/api/v1/users/me/addresses/110/default")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("기본 배송지가 변경되었습니다."))
                    .andExpect(jsonPath("$.data.addressId").value(110))
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }
    }
}
