package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 短信验证码日志状态
 *
 * <p>对应 auth_sms_log.status。
 */
@Getter
@RequiredArgsConstructor
public enum SmsLogStatus implements ValueEnum {

    /** 已发送 */
    SENT("sent", "已发送"),

    /** 已使用 */
    USED("used", "已使用"),

    /** 已过期 */
    EXPIRED("expired", "已过期"),

    /** 发送失败 */
    FAILED("failed", "发送失败");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
