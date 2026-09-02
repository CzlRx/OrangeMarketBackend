package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通用启停状态
 *
 * <p>对应 user_account.status、product_category.status、service_faq.status。
 */
@Getter
@RequiredArgsConstructor
public enum CommonStatus implements ValueEnum {

    /** 启用 */
    ACTIVE("active", "启用"),

    /** 停用 */
    DISABLED("disabled", "停用");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
