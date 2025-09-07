package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    
    private Long id;
    private String email;
    private String name;
    private String phone;
    private String address;
    private User.UserRole role;
    private User.UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
