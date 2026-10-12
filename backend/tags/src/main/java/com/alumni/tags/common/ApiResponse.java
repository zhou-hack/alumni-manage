package com.alumni.tags.common;

/**
 * 统一响应体：{code, message, data, request_id, timestamp}
 * request_id / timestamp 由服务端在响应时刻生成（T+0 Unix 毫秒）。
 */
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
