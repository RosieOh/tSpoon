package com.tspoon.userservice.dto;

/**
 * @param refreshToken 함께 폐기할 Refresh Token. 없으면 지금 쓰던 Access Token 만 차단한다
 */
public record LogoutRequest(String refreshToken) {
}
