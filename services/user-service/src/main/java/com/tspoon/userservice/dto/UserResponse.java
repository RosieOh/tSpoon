package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        String address,
        User.UserRole role,
        User.UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getAddress(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
