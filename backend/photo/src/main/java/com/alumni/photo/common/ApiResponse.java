package com.alumni.photo.common;

public record ApiResponse<T>(String code, String message, T data, String requestId, long timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), "ok", data,
                RequestIdHolder.get(), System.currentTimeMillis());
    }
    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null,
                RequestIdHolder.get(), System.currentTimeMillis());
    }
}
