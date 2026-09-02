package com.czlr.orangemarketbackend.common.enums;

/**
 * 数据库枚举字段的通用约定。
 *
 * <p>实现该接口的枚举，其 {@code value} 与数据库 VARCHAR 列中的值一一对应：
 * 写库时由 MyBatis-Plus 的 {@code @EnumValue} 处理，
 * 返回前端时由 Jackson 的 {@code @JsonValue} 序列化为同一个字符串。
 */
public interface ValueEnum {

    /** 数据库中存储、同时也是返回给前端的值 */
    String getValue();
}
