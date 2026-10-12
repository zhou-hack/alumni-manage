package com.alumni.directory.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码，与《API 规范》第十三章错误码表对齐。
 * HTTP 状态码表达 HTTP 层结果，code 为稳定业务错误码。
 */
public enum ErrorCode {

    OK("OK", HttpStatus.OK),
    INVALID_PARAMETER("INVALID_PARAMETER", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND),
    INVALID_STATE("INVALID_STATE", HttpStatus.CONFLICT),
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
