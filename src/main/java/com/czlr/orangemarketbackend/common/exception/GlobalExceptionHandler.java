package com.czlr.orangemarketbackend.common.exception;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器
 *
 * <p>所有 Controller 抛出的异常统一在这里转成 {@link Result} 响应，
 * HTTP 状态码和业务码来自 {@link ResultCode}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：业务层主动抛出，message 优先使用异常携带的自定义文案 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getResultCode().getCode(), e.getMessage());
        return build(e.getResultCode(), e.getMessage());
    }

    /** 参数校验失败：@Valid 校验不通过 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        return build(ResultCode.BAD_REQUEST, message);
    }

    /** 请求体缺失或 JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return build(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    /** 路径/查询参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ResultCode.BAD_REQUEST, "参数类型错误: " + e.getName());
    }

    /** 兜底：未预期的系统异常，不向客户端暴露堆栈信息 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return build(ResultCode.INTERNAL_SERVER_ERROR, ResultCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    private ResponseEntity<Result<Void>> build(ResultCode resultCode, String message) {
        return ResponseEntity.status(resultCode.getHttpStatus())
                .body(Result.failure(resultCode, message));
    }
}
