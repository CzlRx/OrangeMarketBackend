package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支付交易状态
 *
 * <p>对应 payment_transaction.status。当前为模拟支付，暂不接入真实支付渠道。
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus implements ValueEnum {

    /** 待支付 */
    PENDING("pending", "待支付"),

    /** 支付成功 */
    SUCCESS("success", "支付成功"),

    /** 支付失败 */
    FAILED("failed", "支付失败"),

    /** 已取消 */
    CANCELLED("cancelled", "已取消"),

    /** 已退款 */
    REFUNDED("refunded", "已退款");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
