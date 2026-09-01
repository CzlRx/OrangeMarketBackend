package com.czlr.orangemarketbackend.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 统一业务响应码
 *
 * <p>与接口文档第 2.4 节保持一致：HTTP 状态码和业务错误码同时使用。
 * 成功时业务码为 0，失败时业务码为 5 位数字，前 3 位对应该场景的 HTTP 状态码。
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, HttpStatus.OK, "success"),

    /* ---------- 400 请求参数错误 ---------- */

    /** 请求参数错误 */
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数错误"),

    /** 验证码错误或已过期 */
    SMS_CODE_INVALID(40001, HttpStatus.BAD_REQUEST, "验证码错误或已过期"),

    /** 手机号格式错误 */
    PHONE_INVALID(40002, HttpStatus.BAD_REQUEST, "手机号格式错误"),

    /** 短信发送过于频繁 */
    SMS_TOO_FREQUENT(40003, HttpStatus.BAD_REQUEST, "短信发送过于频繁"),

    /* ---------- 401 未登录或 Token 无效 ---------- */

    /** 未登录或 Token 无效 */
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录或 Token 无效"),

    /* ---------- 403 无权访问 ---------- */

    /** 无权访问该资源 */
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权访问该资源"),

    /* ---------- 404 资源不存在 ---------- */

    /** 资源不存在 */
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),

    /* ---------- 409 数据冲突 ---------- */

    /** 数据冲突、库存不足或重复操作 */
    CONFLICT(40900, HttpStatus.CONFLICT, "数据冲突、库存不足或重复操作"),

    /* ---------- 422 业务状态不允许 ---------- */

    /** 当前业务状态不允许此操作 */
    BUSINESS_STATE_CONFLICT(42200, HttpStatus.UNPROCESSABLE_CONTENT, "当前业务状态不允许此操作"),

    /* ---------- 429 请求频率过高 ---------- */

    /** 请求频率过高 */
    TOO_MANY_REQUESTS(42900, HttpStatus.TOO_MANY_REQUESTS, "请求频率过高"),

    /* ---------- 500 服务端异常 ---------- */

    /** 服务端异常 */
    INTERNAL_SERVER_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务端异常");

    /** 业务响应码，前端据此判断请求结果 */
    private final int code;

    /** 配套的 HTTP 状态码，由异常处理器写入响应 */
    private final HttpStatus httpStatus;

    /** 默认提示信息，业务层可通过 Result 覆盖为更具体的文案 */
    private final String message;
}
