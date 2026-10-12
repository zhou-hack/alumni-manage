package com.alumni.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理（全模块统一）：统一输出 {code, message, data, request_id, timestamp}。
 * 模块化单体内，各模块业务异常均继承 common.BizException，在此统一拦截。
 */
@Order(1)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 任意模块的业务异常：按异常携带的 code / httpStatus 映射统一响应 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
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

    /** 容器层 multipart 超限（单文件 >10MB 或整请求过大）统一映射 FILE_TOO_LARGE */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.httpStatus())
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE, "文件大小超限（单文件 ≤10MB）"));
    }

    /** multipart 请求缺 file 字段 / 非 multipart 调用上传接口 → 400（而非 500） */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMultipartBinding(Exception e) {
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.httpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, "参数缺失或格式错误（file 字段不能为空）"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.error("[debug] common BadRequest: {}", e.getMessage());
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
