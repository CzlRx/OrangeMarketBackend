package com.czlr.orangemarketbackend.common.exception;

import com.czlr.orangemarketbackend.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 *
 * <p>业务层遇到可预期的错误时抛出，携带对应的 {@link ResultCode}，
 * 由 {@link GlobalExceptionHandler} 统一转成 Result 响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 对应的业务响应码 */
    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
