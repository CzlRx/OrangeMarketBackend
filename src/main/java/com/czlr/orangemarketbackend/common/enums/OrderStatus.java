package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单状态
 *
 * <p>对应 orders.status 和 order_status_history.from_status/to_status，
 * 流转规则见接口文档第 14.1 节：
 * <pre>
 * 创建订单       -&gt; pending_payment
 * 支付成功       -&gt; pending_shipment
 * 系统发货       -&gt; pending_receipt
 * 确认收货       -&gt; pending_review
 * 完成评价       -&gt; completed
 * 支付超时       -&gt; cancelled
 * 申请售后       -&gt; refunding
 * 退款成功       -&gt; refunded
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus implements ValueEnum {

    /** 待付款 */
    PENDING_PAYMENT("pending_payment", "待付款"),

    /** 待发货 */
    PENDING_SHIPMENT("pending_shipment", "待发货"),

    /** 待收货 */
    PENDING_RECEIPT("pending_receipt", "待收货"),

    /** 待评价 */
    PENDING_REVIEW("pending_review", "待评价"),

    /** 已完成 */
    COMPLETED("completed", "已完成"),

    /** 已取消 */
    CANCELLED("cancelled", "已取消"),

    /** 退款中 */
    REFUNDING("refunding", "退款中"),

    /** 退款成功 */
    REFUNDED("refunded", "退款成功");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
