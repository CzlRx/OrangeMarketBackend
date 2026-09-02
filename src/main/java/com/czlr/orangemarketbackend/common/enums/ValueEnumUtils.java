package com.czlr.orangemarketbackend.common.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ValueEnum 反查工具：把请求参数或数据库中的字符串还原为枚举。
 *
 * <p>例如 GET /api/orders?status=pending_payment 时，
 * 用 {@code ValueEnumUtils.fromValue(OrderStatus.class, "pending_payment")} 完成转换。
 */
public final class ValueEnumUtils {

    private static final Map<Class<? extends ValueEnum>, Map<String, ValueEnum>> INDEX_CACHE = new ConcurrentHashMap<>();

    private ValueEnumUtils() {
    }

    /**
     * @param enumType 枚举类型
     * @param value    数据库或请求参数中的字符串值
     * @return 对应的枚举常量；value 为空或无匹配时返回 null
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E> & ValueEnum> E fromValue(Class<E> enumType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Map<String, ValueEnum> index = INDEX_CACHE.computeIfAbsent(enumType, type -> {
            Map<String, ValueEnum> map = new HashMap<>();
            for (ValueEnum constant : type.getEnumConstants()) {
                map.put(constant.getValue(), constant);
            }
            return Map.copyOf(map);
        });
        return (E) index.get(value);
    }
}
