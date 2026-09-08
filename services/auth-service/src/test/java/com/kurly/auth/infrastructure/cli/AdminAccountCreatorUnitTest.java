package com.kurly.auth.infrastructure.cli;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import com.kurly.common.exception.InvalidValueException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAccountCreatorUnitTest {

    private static final String VALID_PASSWORD = "Str0ng!Passw0rd";

    @Mock AuthAdminRepository authAdminRepository;
    @Mock PasswordEncoder passwordEncoder;

    private final InputStream originalIn = System.in;

    @AfterEach
    void restoreStdIn() {
        System.setIn(originalIn);
    }

    private void givenPasswordInput(String password) {
        System.setIn(new ByteArrayInputStream((password + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private AdminAccountCreator creator() {
        return new AdminAccountCreator(authAdminRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("계정 생성")
    class CreateTest {

        @Test
        void 인자와_비밀번호로_계정을_만든다() {
            givenPasswordInput(VALID_PASSWORD);
            given(authAdminRepository.findByLoginId("ops-admin")).willReturn(Optional.empty());
            given(passwordEncoder.encode(VALID_PASSWORD)).willReturn("hashed");
            given(authAdminRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            creator().run(new DefaultApplicationArguments(
                    "--admin.login-id=ops-admin", "--admin.admin-id=1001"));

            ArgumentCaptor<AuthAdmin> saved = ArgumentCaptor.forClass(AuthAdmin.class);
            verify(authAdminRepository).save(saved.capture());
            assertThat(saved.getValue().getLoginId()).isEqualTo("ops-admin");
            assertThat(saved.getValue().getAdminId()).isEqualTo(1001L);
        }

        @Test
        void 비밀번호는_해시로_저장된다() {
            givenPasswordInput(VALID_PASSWORD);
            given(authAdminRepository.findByLoginId(anyString())).willReturn(Optional.empty());
            given(passwordEncoder.encode(VALID_PASSWORD)).willReturn("hashed-value");
            given(authAdminRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            creator().run(new DefaultApplicationArguments(
                    "--admin.login-id=ops", "--admin.admin-id=7"));

            ArgumentCaptor<AuthAdmin> saved = ArgumentCaptor.forClass(AuthAdmin.class);
            verify(authAdminRepository).save(saved.capture());
            assertThat(saved.getValue().getPassword()).isEqualTo("hashed-value");
            assertThat(saved.getValue().getPassword()).isNotEqualTo(VALID_PASSWORD);
        }
    }

    @Nested
    @DisplayName("인자 검증")
    class ArgumentTest {

        @Test
        void login_id가_없으면_거부된다() {
            assertThatThrownBy(() -> creator().run(new DefaultApplicationArguments("--admin.admin-id=1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("admin.login-id");
        }

        @Test
        void admin_id가_없으면_거부된다() {
            assertThatThrownBy(() -> creator().run(new DefaultApplicationArguments("--admin.login-id=ops")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("admin.admin-id");
        }

        @Test
        void admin_id가_숫자가_아니면_거부된다() {
            assertThatThrownBy(() -> creator().run(new DefaultApplicationArguments(
                    "--admin.login-id=ops", "--admin.admin-id=not-a-number")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("숫자");
        }

        @Test
        void login_id가_컬럼_길이를_넘으면_거부된다() {
            assertThatThrownBy(() -> creator().run(new DefaultApplicationArguments(
                    "--admin.login-id=" + "x".repeat(51), "--admin.admin-id=1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("50자");
        }
    }

    @Nested
    @DisplayName("생성 거부")
    class RejectionTest {

        @Test
        void 정책에_어긋난_비밀번호는_거부되고_저장되지_않는다() {
            givenPasswordInput("weak");

            assertThatThrownBy(() -> creator().run(new DefaultApplicationArguments(
                    "--admin.login-id=ops", "--admin.admin-id=1")))
                    .isInstanceOf(InvalidValueException.class);

            verify(authAdminRepository, never()).save(any());
        }

        @Test
        void 이미_존재하는_아이디는_거부된다() {
            givenPasswordInput(VALID_PASSWORD);
            given(authAdminRepository.findByLoginId("ops")).willReturn(
                    Optional.of(AuthAdmin.builder().loginId("ops").password("h").adminId(1L).build()));

            assertThatThrownBy(() -> creator().run(new DefaultApplicationArguments(
                    "--admin.login-id=ops", "--admin.admin-id=1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 존재하는");

            verify(authAdminRepository, never()).save(any());
        }
    }
}
