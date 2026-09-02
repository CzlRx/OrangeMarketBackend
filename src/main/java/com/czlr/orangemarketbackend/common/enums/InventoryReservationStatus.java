package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存锁定记录状态
 *
 * <p>对应 inventory_reservation.status：创建订单时锁定，支付成功转已扣减，取消订单转已释放。
 */
@Getter
@RequiredArgsConstructor
public enum InventoryReservationStatus implements ValueEnum {

    /** 已锁定 */
    LOCKED("locked", "已锁定"),

    /** 已释放 */
    RELEASED("released", "已释放"),

    /** 已扣减 */
    DEDUCTED("deducted", "已扣减");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
