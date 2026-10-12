package com.alumni.consents.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一输出 {code, message, data, request_id, timestamp}，
 * HTTP 状态码与业务错误码一一对应。
 */
@RestControllerAdvice("consentsGlobalExceptionHandler")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getErrorCode().httpStatus())
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数缺失或格式错误");
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.httpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.httpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, "参数缺失或格式错误"));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.httpStatus())
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }
}
