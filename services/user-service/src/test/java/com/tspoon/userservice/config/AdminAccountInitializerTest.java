package com.tspoon.userservice.config;

import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminAccountInitializerTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    void 설정이_없으면_아무것도_하지_않는다() {
        run(new AdminProperties(null, null, "관리자"));

        verifyNoInteractions(userRepository);
    }

    @Test
    void 관리자가_없으면_관리자_계정을_만든다() {
        run(new AdminProperties("admin@tspoon.com", "admin-password", "관리자"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(User.UserRole.ADMIN);
        assertThat(saved.getValue().getPassword()).isNotEqualTo("admin-password");
    }

    @Test
    void 관리자가_이미_있으면_만들지_않는다() {
        given(userRepository.existsByRole(User.UserRole.ADMIN)).willReturn(true);

        run(new AdminProperties("admin@tspoon.com", "admin-password", "관리자"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void 같은_이메일의_일반_회원이_있으면_관리자로_바꾸지_않는다() {
        given(userRepository.existsByEmail("admin@tspoon.com")).willReturn(true);

        run(new AdminProperties("admin@tspoon.com", "admin-password", "관리자"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void 비밀번호가_8자보다_짧으면_기동에_실패한다() {
        assertThatThrownBy(() -> run(new AdminProperties("admin@tspoon.com", "short", "관리자")))
                .isInstanceOf(IllegalStateException.class);
    }

    private void run(AdminProperties properties) {
        new AdminAccountInitializer(properties, userRepository, PasswordEncoderFactories.createDelegatingPasswordEncoder())
                .run(new DefaultApplicationArguments());
    }
}
