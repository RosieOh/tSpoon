package com.tspoon.auth;

/**
 * JWT 에서 복원한 인증 사용자. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser user} 로 주입받는다.
 */
public record AuthUser(Long userId, String email, String name, String role) {
}
