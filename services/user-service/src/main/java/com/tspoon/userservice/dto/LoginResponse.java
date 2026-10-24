package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.service.TokenService.IssuedTokens;

/**
 * 로그인·재발급 응답. 재발급할 때마다 Refresh Token 도 새 값으로 바뀌므로 둘 다 갱신해서 써야 한다.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn, // 초 단위
        String refreshToken,
        long refreshExpiresIn, // 초 단위
        Long userId,
        String email,
        User.UserRole role
) {

    public static LoginResponse of(IssuedTokens tokens, User user) {
        return new LoginResponse(
                tokens.accessToken(), "Bearer", tokens.accessTokenTtl().toSeconds(),
                tokens.refreshToken(), tokens.refreshTokenTtl().toSeconds(),
                user.getId(), user.getEmail(), user.getRole());
    }
}
