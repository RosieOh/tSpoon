package com.tspoon.userservice.oauth;

import tools.jackson.databind.JsonNode;

/**
 * 제공자에게서 받은 회원 정보.
 *
 * @param emailVerified 제공자가 이 이메일의 주인임을 확인했는지 — 기존 회원과 자동으로 연결할 때 반드시 확인한다
 */
public record SocialProfile(String provider, String providerUserId, String email, boolean emailVerified, String name) {

    static SocialProfile from(String provider, JsonNode userInfo) {
        return switch (provider) {
            case "kakao" -> kakao(userInfo);
            case "naver" -> naver(userInfo);
            default -> throw new IllegalArgumentException("모르는 제공자: " + provider);
        };
    }

    // {"id": 123, "kakao_account": {"email": "…", "is_email_valid": true, "is_email_verified": true, "profile": {"nickname": "…"}}}
    private static SocialProfile kakao(JsonNode userInfo) {
        JsonNode account = userInfo.path("kakao_account");
        boolean verified = account.path("is_email_verified").asBoolean(false) && account.path("is_email_valid").asBoolean(true);
        return new SocialProfile("kakao", userInfo.required("id").asString(), account.path("email").asString(null), verified,
                account.path("profile").path("nickname").asString("카카오 회원"));
    }

    // {"resultcode": "00", "response": {"id": "…", "email": "…", "name": "…", "nickname": "…"}} — 네이버는 인증된 이메일만 준다
    private static SocialProfile naver(JsonNode userInfo) {
        if (!"00".equals(userInfo.path("resultcode").asString(""))) {
            throw new IllegalStateException("네이버 회원 정보 조회 실패: " + userInfo.path("message").asString(""));
        }
        JsonNode response = userInfo.required("response");
        String email = response.path("email").asString(null);
        String name = response.path("name").asString(response.path("nickname").asString("네이버 회원"));
        return new SocialProfile("naver", response.required("id").asString(), email, email != null, name);
    }
}
