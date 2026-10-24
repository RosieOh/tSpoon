package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.User;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "역할은 필수입니다")
        User.UserRole role
) {
}
