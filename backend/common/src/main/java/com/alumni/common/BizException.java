package com.alumni.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常基类（模块化单体统一异常体系）。
 *
 * 携带稳定业务错误码与对应 HTTP 状态码，由全局异常处理器转为统一响应。
 * 各业务模块的 BizException 继承本类（保留各自模块的 ErrorCode 枚举与 getErrorCode()），
 * 使单体内的 common.GlobalExceptionHandler 能统一拦截所有模块的业务异常。
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.code(), errorCode.httpStatus(), message);
    }

    public BizException(String code, HttpStatus httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
