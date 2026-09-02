package com.czlr.orangemarketbackend.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 秒杀活动状态
 *
 * <p>对应 seckill_activity.status。
 */
@Getter
@RequiredArgsConstructor
public enum SeckillActivityStatus implements ValueEnum {

    /** 草稿 */
    DRAFT("draft", "草稿"),

    /** 未开始 */
    NOT_STARTED("not_started", "未开始"),

    /** 进行中 */
    RUNNING("running", "进行中"),

    /** 已结束 */
    ENDED("ended", "已结束"),

    /** 已售罄 */
    SOLD_OUT("sold_out", "已售罄");

    /** 数据库存储值，同时是 API 返回给前端的字符串 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 中文说明，用于日志和展示 */
    private final String description;
}
