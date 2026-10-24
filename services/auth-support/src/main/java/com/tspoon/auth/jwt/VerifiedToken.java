package com.tspoon.auth.jwt;

import com.tspoon.auth.AuthUser;

import java.time.Duration;
import java.time.Instant;

/**
 * 서명·만료·발급자 검증을 통과한 Access Token.
 *
 * @param tokenId 토큰 고유 ID(jti). 로그아웃 시 차단 목록 키로 쓴다
 * @param version 발급 당시 회원의 토큰 버전. 정지·역할 변경으로 버전이 올라가면 이전 토큰은 무효
 */
public record VerifiedToken(AuthUser user, String tokenId, long version, Instant expiresAt) {

    public Duration remainingLifetime(Instant now) {
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
