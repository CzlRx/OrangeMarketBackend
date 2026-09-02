package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客服会话状态
 *
 * <p>对应 service_session.status。
 */
@Getter
@RequiredArgsConstructor
public enum ServiceSessionStatus implements ValueEnum {

    /** 进行中 */
    ACTIVE("active", "进行中"),

    /** 已关闭 */
    CLOSED("closed", "已关闭");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
