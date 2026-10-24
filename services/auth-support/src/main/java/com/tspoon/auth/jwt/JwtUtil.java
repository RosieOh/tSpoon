package com.tspoon.auth.jwt;

import com.tspoon.auth.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class JwtUtil {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_VERSION = "ver";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtUtil(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.accessTokenTtl = properties.accessTokenTtl();
        this.refreshTokenTtl = properties.refreshTokenTtl();
    }

    /**
     * @param tokenVersion 회원의 현재 토큰 버전. Gateway 가 이 값이 현재 버전보다 낮은 토큰을 거부한다
     */
    public String generateAccessToken(AuthUser user, long tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.userId()))
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_NAME, user.name())
                .claim(CLAIM_ROLE, user.role())
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 토큰을 폐기한 적이 없는 회원(버전 0)의 토큰을 만든다.
     */
    public String generateAccessToken(AuthUser user) {
        return generateAccessToken(user, 0);
    }

    /**
     * 서명·만료·발급자를 검증한다. 유효하지 않으면 empty.
     * 로그아웃·정지로 폐기됐는지는 확인하지 않는다 (Redis 를 보는 api-gateway 의 몫).
     */
    public Optional<VerifiedToken> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            AuthUser user = new AuthUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    claims.get(CLAIM_ROLE, String.class));
            Number version = claims.get(CLAIM_VERSION, Number.class);
            return Optional.of(new VerifiedToken(
                    user, claims.getId(), version == null ? 0 : version.longValue(), claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AuthUser> parseAccessToken(String token) {
        return verify(token).map(VerifiedToken::user);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }
}
