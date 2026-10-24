package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.User;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn, // 초 단위
        Long userId,
        String email,
        User.UserRole role
) {

    public static LoginResponse of(String accessToken, long expiresInSeconds, User user) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, user.getId(), user.getEmail(), user.getRole());
    }
}
