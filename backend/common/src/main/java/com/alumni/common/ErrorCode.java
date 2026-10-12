package com.alumni.common;

import org.springframework.http.HttpStatus;

/**
 * 全平台统一业务错误码，与《API 规范》第十三章错误码表对齐（各模块子集合并全集）。
 * HTTP 状态码表达 HTTP 层结果，code 为稳定业务错误码。
 */
public enum ErrorCode {

    OK("OK", HttpStatus.OK),
    INVALID_PARAMETER("INVALID_PARAMETER", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE("FILE_TOO_LARGE", HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED", HttpStatus.BAD_REQUEST),
    BATCH_LIMIT_EXCEEDED("BATCH_LIMIT_EXCEEDED", HttpStatus.BAD_REQUEST),
    AUTH_VERIFY_FAILED("AUTH_VERIFY_FAILED", HttpStatus.BAD_REQUEST),
    AUTH_CODE_INVALID("AUTH_CODE_INVALID", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND),
    INVALID_STATE("INVALID_STATE", HttpStatus.CONFLICT),
    RESOURCE_REFERENCED("RESOURCE_REFERENCED", HttpStatus.CONFLICT),
    AUTH_ALREADY_ACTIVATED("AUTH_ALREADY_ACTIVATED", HttpStatus.CONFLICT),
    RATE_LIMITED("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS),
    // P1 新增（《[定稿]API规范P1》1.2 节）
    TAG_DUPLICATED("TAG_DUPLICATED", HttpStatus.CONFLICT),
    SCOPE_REQUIRED("SCOPE_REQUIRED", HttpStatus.BAD_REQUEST),
    HANDOVER_INVALID("HANDOVER_INVALID", HttpStatus.CONFLICT),
    EXPORT_LIMIT_EXCEEDED("EXPORT_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;

    ErrorCode(String code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
