package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 秒杀购买记录状态
 *
 * <p>对应 seckill_user_purchase.status。取消订单后记录回到已取消，用户可以重新抢购。
 */
@Getter
@RequiredArgsConstructor
public enum SeckillPurchaseStatus implements ValueEnum {

    /** 已锁定 */
    LOCKED("locked", "已锁定"),

    /** 抢购成功 */
    SUCCESS("success", "抢购成功"),

    /** 已取消 */
    CANCELLED("cancelled", "已取消");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
