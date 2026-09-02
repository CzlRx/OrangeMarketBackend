package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 退款流水状态
 *
 * <p>对应 refund_transaction.status。当前为模拟退款，不接入真实支付渠道。
 */
@Getter
@RequiredArgsConstructor
public enum RefundStatus implements ValueEnum {

    /** 退款中 */
    PENDING("pending", "退款中"),

    /** 退款成功 */
    SUCCESS("success", "退款成功"),

    /** 退款失败 */
    FAILED("failed", "退款失败");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
