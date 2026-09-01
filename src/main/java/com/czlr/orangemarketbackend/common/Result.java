package com.czlr.orangemarketbackend.common;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"code", "message", "data", "timestamp"})
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    @SuppressWarnings("unchecked")
    public static <T> Result<T> success() {
        return new Result<>(
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(),
                (T) Collections.emptyMap(),
                System.currentTimeMillis());
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(),
                data,
                System.currentTimeMillis());
    }

    public static <T> Result<T> failure(ResultCode resultCode) {
        return failure(resultCode, resultCode.getMessage());
    }

    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return new Result<>(
                resultCode.getCode(),
                message,
                null,
                System.currentTimeMillis());
    }
}
