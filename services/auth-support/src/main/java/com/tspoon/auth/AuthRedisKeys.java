package com.tspoon.auth;

/**
 * 토큰 발급·폐기(user-service)와 폐기 확인(api-gateway)이 함께 쓰는 Redis 키.
 *
 * <pre>
 * refresh:{해시}        → "{회원ID}:{계열ID}"   아직 쓰지 않은 Refresh Token (원문 대신 SHA-256 해시로 저장)
 * refresh-used:{해시}   → 계열ID               이미 쓴 Refresh Token. 다시 오면 탈취로 보고 계열을 폐기
 * family:{계열ID}       → 회원ID               로그인 한 번에서 이어지는 Refresh 회전 묶음. 없으면 폐기된 것
 * user-families:{회원ID} → {계열ID...}          정지 시 회원의 모든 계열을 폐기하기 위한 목록
 * denylist:{jti}        → "1"                  로그아웃한 Access Token (남은 수명만큼 보관)
 * token-version:{회원ID} → 숫자                 이보다 낮은 버전의 Access Token 은 무효 (정지·역할 변경 시 증가)
 * </pre>
 */
public final class AuthRedisKeys {

    private static final String PREFIX = "tspoon:auth:";

    private AuthRedisKeys() {
    }

    public static String refreshToken(String tokenHash) {
        return PREFIX + "refresh:" + tokenHash;
    }

    public static String usedRefreshToken(String tokenHash) {
        return PREFIX + "refresh-used:" + tokenHash;
    }

    public static String family(String familyId) {
        return PREFIX + "family:" + familyId;
    }

    public static String userFamilies(Long userId) {
        return PREFIX + "user-families:" + userId;
    }

    public static String accessDenylist(String tokenId) {
        return PREFIX + "denylist:" + tokenId;
    }

    public static String tokenVersion(Long userId) {
        return PREFIX + "token-version:" + userId;
    }
}
