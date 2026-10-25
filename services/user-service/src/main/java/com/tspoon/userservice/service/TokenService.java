package com.tspoon.userservice.service;

import com.tspoon.auth.AuthRedisKeys;
import com.tspoon.auth.AuthUser;
import com.tspoon.auth.jwt.JwtUtil;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.userservice.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Access·Refresh Token 발급과 폐기. 저장 구조는 {@link AuthRedisKeys} 참고.
 *
 * Refresh Token 은 쓸 때마다 새 값으로 바꾸고(RTR), 이미 쓴 값이 다시 오면 탈취로 보고 그 계열 전체를 폐기한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redis;
    private final JwtUtil jwtUtil;

    /** 로그인: 새 Refresh 계열을 연다 */
    public IssuedTokens startSession(User user) {
        String familyId = UUID.randomUUID().toString();
        Duration ttl = jwtUtil.getRefreshTokenTtl();
        redis.opsForValue().set(AuthRedisKeys.family(familyId), String.valueOf(user.getId()), ttl);
        redis.opsForSet().add(AuthRedisKeys.userFamilies(user.getId()), familyId);
        redis.expire(AuthRedisKeys.userFamilies(user.getId()), ttl);
        return issue(user, familyId);
    }

    /**
     * Refresh Token 을 한 번 쓰고 버린다. GETDEL 로 꺼내므로 같은 토큰으로 동시에 요청해도 하나만 성공한다.
     */
    public RefreshTokenOwner consume(String refreshToken) {
        String hash = SecureTokens.sha256(refreshToken);
        String value = redis.opsForValue().getAndDelete(AuthRedisKeys.refreshToken(hash));

        if (value == null) {
            String reusedFamily = redis.opsForValue().get(AuthRedisKeys.usedRefreshToken(hash));
            if (reusedFamily != null) {
                log.warn("이미 사용한 Refresh Token 이 다시 들어와 토큰 계열 {} 을 폐기합니다.", reusedFamily);
                revokeFamily(reusedFamily);
            }
            throw invalidRefreshToken();
        }

        RefreshTokenOwner owner = RefreshTokenOwner.parse(value);
        redis.opsForValue().set(AuthRedisKeys.usedRefreshToken(hash), owner.familyId(), jwtUtil.getRefreshTokenTtl());
        if (!Boolean.TRUE.equals(redis.hasKey(AuthRedisKeys.family(owner.familyId())))) {
            throw invalidRefreshToken(); // 로그아웃·정지 등으로 계열이 이미 폐기됨
        }
        return owner;
    }

    /** 같은 계열 안에서 새 Access·Refresh Token 을 발급한다 */
    public IssuedTokens issue(User user, String familyId) {
        AuthUser authUser = new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getRole().name());
        String accessToken = jwtUtil.generateAccessToken(authUser, currentVersion(user.getId()));

        String refreshToken = SecureTokens.generate();
        redis.opsForValue().set(AuthRedisKeys.refreshToken(SecureTokens.sha256(refreshToken)),
                RefreshTokenOwner.format(user.getId(), familyId), jwtUtil.getRefreshTokenTtl());

        return new IssuedTokens(accessToken, jwtUtil.getAccessTokenTtl(), refreshToken, jwtUtil.getRefreshTokenTtl());
    }

    /**
     * 로그아웃: Refresh 계열을 폐기하고, 지금 쓰던 Access Token 은 남은 수명만큼 차단 목록에 올린다.
     * 다른 회원의 Refresh Token 이면 건드리지 않는다.
     */
    public void logout(Long userId, String accessToken, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String key = AuthRedisKeys.refreshToken(SecureTokens.sha256(refreshToken));
            String value = redis.opsForValue().get(key);
            if (value != null) {
                RefreshTokenOwner owner = RefreshTokenOwner.parse(value);
                if (owner.userId().equals(userId)) {
                    redis.delete(key);
                    revokeFamily(owner.familyId());
                }
            }
        }

        jwtUtil.verify(accessToken).ifPresent(token -> {
            Duration remaining = token.remainingLifetime(Instant.now());
            if (!remaining.isZero()) {
                redis.opsForValue().set(AuthRedisKeys.accessDenylist(token.tokenId()), "1", remaining);
            }
        });
    }

    /** 이미 발급된 Access Token 을 모두 무효화한다 (역할 변경). Refresh 는 살려 두어 재발급하면 새 역할이 담긴다 */
    public void invalidateAccessTokens(Long userId) {
        redis.opsForValue().increment(AuthRedisKeys.tokenVersion(userId));
    }

    /** Access Token 무효화 + 모든 Refresh 계열 폐기 (정지·비활성화) */
    public void revokeAll(Long userId) {
        invalidateAccessTokens(userId);
        Set<String> families = redis.opsForSet().members(AuthRedisKeys.userFamilies(userId));
        if (families != null) {
            families.forEach(familyId -> redis.delete(AuthRedisKeys.family(familyId)));
        }
        redis.delete(AuthRedisKeys.userFamilies(userId));
    }

    public long currentVersion(Long userId) {
        String version = redis.opsForValue().get(AuthRedisKeys.tokenVersion(userId));
        return version == null ? 0 : Long.parseLong(version);
    }

    private void revokeFamily(String familyId) {
        String userId = redis.opsForValue().getAndDelete(AuthRedisKeys.family(familyId));
        if (userId != null) {
            redis.opsForSet().remove(AuthRedisKeys.userFamilies(Long.valueOf(userId)), familyId);
        }
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException("Refresh Token 이 유효하지 않습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED);
    }

    public record IssuedTokens(String accessToken, Duration accessTokenTtl, String refreshToken, Duration refreshTokenTtl) {
    }

    public record RefreshTokenOwner(Long userId, String familyId) {

        static String format(Long userId, String familyId) {
            return userId + ":" + familyId;
        }

        static RefreshTokenOwner parse(String value) {
            int separator = value.indexOf(':');
            return new RefreshTokenOwner(Long.valueOf(value.substring(0, separator)), value.substring(separator + 1));
        }
    }
}
