package com.tspoon.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private Long userId;
    private String email;
    private String role;
    
    public static LoginResponse of(String accessToken, String refreshToken, 
                                 Long expiresIn, Long userId, String email, String role) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .userId(userId)
                .email(email)
                .role(role)
                .build();
    }
}
