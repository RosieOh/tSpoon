package com.tspoon.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 메일 링크로 보내는 일회용 토큰(이메일 인증, 비밀번호 재설정). 저장 구조는 {@link UserRedisKeys} 참고.
 * 회원마다 용도별로 마지막에 발급한 토큰 하나만 유효하다.
 */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private final StringRedisTemplate redis;

    public IssuedToken issue(Purpose purpose, Long userId, Duration ttl) {
        String token = SecureTokens.generate();
        String hash = SecureTokens.sha256(token);

        redis.opsForValue().set(UserRedisKeys.oneTimeToken(purpose.key, hash), String.valueOf(userId), ttl);
        String previous = redis.opsForValue().getAndSet(UserRedisKeys.latestOneTimeToken(purpose.key, userId), hash);
        redis.expire(UserRedisKeys.latestOneTimeToken(purpose.key, userId), ttl);
        if (previous != null && !previous.equals(hash)) {
            redis.delete(UserRedisKeys.oneTimeToken(purpose.key, previous));
        }
        return new IssuedToken(token, Instant.now().plus(ttl));
    }

    /** 토큰을 한 번 쓰고 버린다. GETDEL 로 꺼내므로 같은 토큰으로 동시에 요청해도 하나만 성공한다 */
    public Optional<Long> consume(Purpose purpose, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String userId = redis.opsForValue().getAndDelete(UserRedisKeys.oneTimeToken(purpose.key, SecureTokens.sha256(token)));
        if (userId == null) {
            return Optional.empty();
        }
        redis.delete(UserRedisKeys.latestOneTimeToken(purpose.key, Long.valueOf(userId)));
        return Optional.of(Long.valueOf(userId));
    }

    public enum Purpose {
        EMAIL_VERIFICATION("email-verification"),
        PASSWORD_RESET("password-reset");

        private final String key;

        Purpose(String key) {
            this.key = key;
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
