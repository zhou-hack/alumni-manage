package com.alumni.stats.common;

import lombok.Getter;

/**
 * 业务异常：携带稳定业务错误码，由全局异常处理器转为统一响应。
 * 继承 common.BizException，模块化单体内由 common 的全局异常处理器统一拦截。
 */
@Getter
public class BizException extends com.alumni.common.BizException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode, String message) {
        super(errorCode.code(), errorCode.httpStatus(), message);
        this.errorCode = errorCode;
    }
}
