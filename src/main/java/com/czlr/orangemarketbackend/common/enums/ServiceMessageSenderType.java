package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客服消息发送方
 *
 * <p>对应 service_message.sender_type。
 */
@Getter
@RequiredArgsConstructor
public enum ServiceMessageSenderType implements ValueEnum {

    USER("user", "用户"),
    AGENT("agent", "客服"),
    SYSTEM("system", "系统");

    @EnumValue
    @JsonValue
    private final String value;

    private final String description;
}
