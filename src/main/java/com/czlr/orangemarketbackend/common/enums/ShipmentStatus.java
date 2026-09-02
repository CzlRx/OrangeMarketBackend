package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 物流状态
 *
 * <p>对应 order_shipment.status。真实物流平台回调接口暂不实现，由系统直接写入。
 */
@Getter
@RequiredArgsConstructor
public enum ShipmentStatus implements ValueEnum {

    /** 已发货 */
    SHIPPED("shipped", "已发货"),

    /** 已签收 */
    DELIVERED("delivered", "已签收");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
