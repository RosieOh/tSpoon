package com.tspoon.userservice.service;

import com.tspoon.core.exception.TooManyRequestsException;
import com.tspoon.userservice.config.LoginLockProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 계정(이메일)별 로그인 실패 횟수. 기간 안에 연속으로 기준 횟수만큼 틀리면 일정 시간 잠가 비밀번호 무차별 대입을 막는다.
 * 가입하지 않은 이메일도 똑같이 세고 잠가서 잠김 여부로 가입 여부가 드러나지 않게 한다.
 * 여러 계정을 돌아가며 시도하는 공격은 Gateway 의 IP 별 요청 제한이 막는다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redis;
    private final LoginLockProperties properties;

    /** 잠겨 있으면 비밀번호가 맞더라도 429 */
    public void checkNotLocked(String email) {
        String key = key(email);
        String failures = redis.opsForValue().get(key);
        if (failures != null && Long.parseLong(failures) >= properties.maxFailures()) {
            Duration remaining = remaining(key);
            throw new TooManyRequestsException(
                    "로그인에 여러 번 실패해 잠시 잠겼습니다. " + Math.max(1, remaining.toMinutes()) + "분 뒤에 다시 시도하거나 비밀번호를 재설정해 주세요.",
                    "LOGIN_LOCKED", remaining);
        }
    }

    public void recordFailure(String email) {
        String key = key(email);
        Long failures = redis.opsForValue().increment(key);
        if (failures == null) {
            return;
        }
        if (failures == 1) {
            redis.expire(key, properties.window());
        } else if (failures == properties.maxFailures()) {
            redis.expire(key, properties.lockDuration()); // 마지막 실패부터 잠금 시간을 센다
        }
    }

    /** 로그인에 성공하거나 비밀번호를 재설정하면 실패 횟수를 지운다 */
    public void reset(String email) {
        redis.delete(key(email));
    }

    private Duration remaining(String key) {
        Long seconds = redis.getExpire(key);
        return seconds == null || seconds < 0 ? properties.lockDuration() : Duration.ofSeconds(seconds);
    }

    // 키에 이메일 원문을 남기지 않는다
    private static String key(String email) {
        return UserRedisKeys.loginFailures(SecureTokens.sha256(email.strip().toLowerCase(Locale.ROOT)));
    }
}
