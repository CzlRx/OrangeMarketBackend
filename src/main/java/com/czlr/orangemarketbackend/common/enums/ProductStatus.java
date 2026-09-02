package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品状态
 *
 * <p>对应 product.status。
 */
@Getter
@RequiredArgsConstructor
public enum ProductStatus implements ValueEnum {

    /** 草稿 */
    DRAFT("draft", "草稿"),

    /** 在售 */
    ON_SALE("on_sale", "在售"),

    /** 下架 */
    OFF_SALE("off_sale", "下架");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
