package com.kurly.auth.infrastructure.client;

import com.kurly.auth.application.port.UserProfileClient;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.support.StubHttpServer;
import com.kurly.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceProfileClientUnitTest {

    private StubHttpServer stub;

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.close();
        }
    }

    @Nested
    @DisplayName("프로필 동기화 성공")
    class SyncTest {

        @Test
        void 회원_도메인_id와_신규여부를_돌려준다() {
            stub = new StubHttpServer().stub("/internal/v1/users/sync-profile", 200, """
                    {"status":"SUCCESS","data":{"userId":10023,"isNewUser":true},"error":null}""");
            UserServiceProfileClient client = new UserServiceProfileClient(stub.url(""));

            UserProfileClient.SyncedProfile profile = client.syncProfile(AuthProvider.KAKAO, "pid-1");

            assertThat(profile.userId()).isEqualTo(10023L);
            assertThat(profile.newUser()).isTrue();
        }

        @Test
        void 기존_회원이면_신규여부가_false다() {
            stub = new StubHttpServer().stub("/internal/v1/users/sync-profile", 200, """
                    {"status":"SUCCESS","data":{"userId":7,"isNewUser":false}}""");
            UserServiceProfileClient client = new UserServiceProfileClient(stub.url(""));

            assertThat(client.syncProfile(AuthProvider.NAVER, "pid-2").newUser()).isFalse();
        }

        @Test
        void 소셜_식별자를_본문으로_전달한다() {
            stub = new StubHttpServer().stub("/internal/v1/users/sync-profile", 200, """
                    {"data":{"userId":1,"isNewUser":false}}""");
            UserServiceProfileClient client = new UserServiceProfileClient(stub.url(""));

            client.syncProfile(AuthProvider.KAKAO, "pid-3");

            assertThat(stub.lastReceived().body()).contains("\"provider\":\"KAKAO\"").contains("pid-3");
        }
    }

    @Nested
    @DisplayName("프로필 동기화 실패")
    class SyncFailureTest {

        @Test
        void user_service가_오류를_반환하면_내부_오류로_변환된다() {
            stub = new StubHttpServer().stub("/internal/v1/users/sync-profile", 503, "{}");
            UserServiceProfileClient client = new UserServiceProfileClient(stub.url(""));

            assertThatThrownBy(() -> client.syncProfile(AuthProvider.KAKAO, "pid"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("회원 정보 처리 중 오류");
        }

        @Test
        void userId가_없는_응답은_거부된다() {
            stub = new StubHttpServer().stub("/internal/v1/users/sync-profile", 200, """
                    {"status":"SUCCESS","data":{}}""");
            UserServiceProfileClient client = new UserServiceProfileClient(stub.url(""));

            assertThatThrownBy(() -> client.syncProfile(AuthProvider.KAKAO, "pid"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
