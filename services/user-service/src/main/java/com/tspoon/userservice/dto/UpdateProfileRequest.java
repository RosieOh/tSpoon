package com.tspoon.userservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "이름은 필수입니다")
        String name,

        String phone,
        String address
) {
}
