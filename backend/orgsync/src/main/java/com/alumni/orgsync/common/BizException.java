package com.alumni.orgsync.common;

import lombok.Getter;
@Getter
public class BizException extends com.alumni.common.BizException {
    private final ErrorCode errorCode;
    public BizException(ErrorCode errorCode, String message) {
        super(errorCode.code(), errorCode.httpStatus(), message);
        this.errorCode = errorCode;
    }
}
