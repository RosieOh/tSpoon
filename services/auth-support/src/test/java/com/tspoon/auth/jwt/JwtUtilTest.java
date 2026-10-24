package com.tspoon.auth.jwt;

import com.tspoon.auth.AuthUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-jwt-util-0123456789";
    private static final AuthUser USER = new AuthUser(42L, "parent@tspoon.com", "김부모", "PARENT");

    private final JwtUtil jwtUtil = jwtUtil(SECRET, "tspoon", Duration.ofMinutes(30));

    @Test
    void 발급한_토큰에서_사용자_정보를_복원한다() {
        String token = jwtUtil.generateAccessToken(USER);

        assertThat(jwtUtil.parseAccessToken(token)).contains(USER);
    }

    @Test
    void 토큰마다_고유_ID와_발급_시점의_토큰_버전이_담긴다() {
        VerifiedToken first = jwtUtil.verify(jwtUtil.generateAccessToken(USER, 3)).orElseThrow();
        VerifiedToken second = jwtUtil.verify(jwtUtil.generateAccessToken(USER, 3)).orElseThrow();

        assertThat(first.tokenId()).isNotBlank().isNotEqualTo(second.tokenId());
        assertThat(first.version()).isEqualTo(3);
        assertThat(first.remainingLifetime(Instant.now())).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
    }

    @Test
    void 서명이_변조된_토큰은_거부한다() {
        String token = jwtUtil.generateAccessToken(USER);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwtUtil.verify(tampered)).isEmpty();
    }

    @Test
    void 다른_키로_서명된_토큰은_거부한다() {
        JwtUtil other = jwtUtil("another-secret-key-for-jwt-0123456789", "tspoon", Duration.ofMinutes(30));

        assertThat(jwtUtil.verify(other.generateAccessToken(USER))).isEmpty();
    }

    @Test
    void 발급자가_다른_토큰은_거부한다() {
        JwtUtil other = jwtUtil(SECRET, "someone-else", Duration.ofMinutes(30));

        assertThat(jwtUtil.verify(other.generateAccessToken(USER))).isEmpty();
    }

    @Test
    void 만료된_토큰은_거부한다() {
        JwtUtil expired = jwtUtil(SECRET, "tspoon", Duration.ofSeconds(-1));

        assertThat(jwtUtil.verify(expired.generateAccessToken(USER))).isEmpty();
    }

    @Test
    void 형식이_잘못된_토큰은_거부한다() {
        assertThat(jwtUtil.verify("not-a-jwt")).isEmpty();
        assertThat(jwtUtil.verify("")).isEmpty();
    }

    @Test
    void 비밀키가_256비트보다_짧으면_기동_시점에_실패한다() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "tspoon", Duration.ofMinutes(30), Duration.ofDays(14)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JwtUtil jwtUtil(String secret, String issuer, Duration accessTokenTtl) {
        return new JwtUtil(new JwtProperties(secret, issuer, accessTokenTtl, Duration.ofDays(14)));
    }
}
