package com.tspoon.userservice.config;

import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 관리자가 한 명도 없을 때만 초기 관리자 계정을 만든다.
 * 이미 관리자가 있거나 같은 이메일의 회원이 있으면 아무것도 바꾸지 않는다 (재기동해도 비밀번호를 덮어쓰지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD 는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        if (userRepository.existsByRole(User.UserRole.ADMIN)) {
            log.debug("관리자 계정이 이미 있어 초기 관리자를 만들지 않습니다.");
            return;
        }
        if (userRepository.existsByEmail(properties.email())) {
            log.warn("ADMIN_EMAIL({})이 이미 일반 회원으로 가입돼 있어 초기 관리자를 만들지 않습니다.", properties.email());
            return;
        }

        userRepository.save(User.builder()
                .email(properties.email())
                .password(passwordEncoder.encode(properties.password()))
                .name(properties.name())
                .role(User.UserRole.ADMIN)
                .status(User.UserStatus.ACTIVE)
                .emailVerifiedAt(Instant.now()) // 운영자가 직접 넣은 주소라 인증 메일을 보내지 않는다
                .build());
        log.info("초기 관리자 계정을 만들었습니다: {}", properties.email());
    }
}
