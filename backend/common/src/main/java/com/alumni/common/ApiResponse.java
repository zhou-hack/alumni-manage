package com.alumni.common;

/**
 * 统一响应体：{code, message, data, request_id, timestamp}
 */
public record ApiResponse<T>(String code, String message, T data, String requestId, long timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), "ok", data,
                RequestIdHolder.get(), System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null,
                RequestIdHolder.get(), System.currentTimeMillis());
    }

    /** 按稳定业务错误码字符串构造（供统一异常处理按 BizException.code 使用） */
    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null,
                RequestIdHolder.get(), System.currentTimeMillis());
    }
}
