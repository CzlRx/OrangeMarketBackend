package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 售后申请状态
 *
 * <p>对应 after_sale_request.status，也是 GET /api/after-sales 的 status 筛选值。
 */
@Getter
@RequiredArgsConstructor
public enum AfterSaleStatus implements ValueEnum {

    /** 待处理 */
    PENDING("pending", "待处理"),

    /** 已同意 */
    APPROVED("approved", "已同意"),

    /** 已拒绝 */
    REJECTED("rejected", "已拒绝"),

    /** 已退款 */
    REFUNDED("refunded", "已退款"),

    /** 已取消 */
    CANCELLED("cancelled", "已取消");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
