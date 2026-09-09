package com.kurly.user.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserInternalControllerUnitTest {

    private static final String BODY = """
            {"provider":"KAKAO","providerId":"1234567890"}""";

    @Mock UserProfileService userProfileService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserInternalController(userProfileService))
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
}
