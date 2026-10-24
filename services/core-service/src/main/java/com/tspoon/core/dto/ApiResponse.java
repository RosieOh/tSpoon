package com.tspoon.core.dto;

public record ApiResponse<T>(boolean success, String message, T data, int status) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "요청이 성공적으로 처리되었습니다.", data, 200);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, 200);
    }

    public static <T> ApiResponse<T> error(String message, int status) {
        return new ApiResponse<>(false, message, null, status);
    }

    public static <T> ApiResponse<T> error(String message, int status, T data) {
        return new ApiResponse<>(false, message, data, status);
    }
}
