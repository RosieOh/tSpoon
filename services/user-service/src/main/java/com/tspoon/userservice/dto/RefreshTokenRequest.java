package com.tspoon.userservice.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token 은 필수입니다")
        String refreshToken
) {
}
