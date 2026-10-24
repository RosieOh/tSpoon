package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.User;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull(message = "상태는 필수입니다")
        User.UserStatus status
) {
}
