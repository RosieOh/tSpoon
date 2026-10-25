package com.tspoon.userservice;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 이메일 인증이 주제가 아닌 테스트에서 가입한 회원을 바로 인증된 상태로 만든다.
 * 인증 흐름 자체는 {@link EmailVerificationIntegrationTest} 가 확인한다.
 */
final class TestAccounts {

    private TestAccounts() {
    }

    static void verifyEmail(JdbcTemplate jdbcTemplate, String email) {
        jdbcTemplate.update("UPDATE users SET email_verified_at = now() WHERE email = ?", email);
    }
}
