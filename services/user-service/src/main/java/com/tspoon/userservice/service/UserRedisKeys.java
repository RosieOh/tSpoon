package com.tspoon.userservice.service;

/**
 * user-service 만 쓰는 Redis 키. 토큰 발급·폐기용 키는 Gateway 와 함께 쓰므로 auth-support 의 AuthRedisKeys 에 있다.
 *
 * <pre>
 * one-time:{용도}:{해시}          → 회원ID   메일 링크 토큰. 한 번 쓰면(GETDEL) 사라짐, 원문 대신 SHA-256 해시로 저장
 * one-time-latest:{용도}:{회원ID} → 해시     회원의 가장 최근 토큰. 새로 발급하면 이전 토큰을 지운다
 * verification-cooldown:{회원ID}  → "1"      인증 메일 재발송 대기 시간
 * </pre>
 */
public final class UserRedisKeys {

    private static final String PREFIX = "tspoon:user:";

    private UserRedisKeys() {
    }

    public static String oneTimeToken(String purpose, String tokenHash) {
        return PREFIX + "one-time:" + purpose + ":" + tokenHash;
    }

    public static String latestOneTimeToken(String purpose, Long userId) {
        return PREFIX + "one-time-latest:" + purpose + ":" + userId;
    }

    public static String verificationCooldown(Long userId) {
        return PREFIX + "verification-cooldown:" + userId;
    }
}
