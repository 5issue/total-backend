package com.kurly.user.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserInternalControllerUnitExceptionTest {

    @Mock UserProfileService userProfileService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserInternalController(userProfileService))
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
}
