package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 评价状态
 *
 * <p>对应 product_review.status。商品列表和评价列表只返回可见评价。
 */
@Getter
@RequiredArgsConstructor
public enum ReviewStatus implements ValueEnum {

    /** 可见 */
    VISIBLE("visible", "可见"),

    /** 隐藏 */
    HIDDEN("hidden", "隐藏"),

    /** 待审核 */
    PENDING("pending", "待审核");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
