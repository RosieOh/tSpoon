package com.tspoon.userservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailVerificationRequest(
        @NotBlank(message = "token 은 필수입니다")
        String token
) {
}
