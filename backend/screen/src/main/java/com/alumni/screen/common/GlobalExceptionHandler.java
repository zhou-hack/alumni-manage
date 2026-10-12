package com.alumni.screen.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：将 BizException、参数校验异常等转为 ApiResponse 统一响应。
 */
@RestControllerAdvice("screenGlobalExceptionHandler")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        log.debug("biz exception: code={} msg={}", e.getErrorCode().code(), e.getMessage());
        return ResponseEntity.status(e.getErrorCode().httpStatus())
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAny(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }
}
