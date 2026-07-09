package com.bupt.ecommerce.ai.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

record ProductContext(
        Long productId,
        String name,
        String description,
        BigDecimal price,
        String status,
        Integer availableStock
) {

    static ProductContext from(Map<String, Object> data, Long fallbackProductId) {
        Map<?, ?> stock = data.get("stock") instanceof Map<?, ?> value ? value : Map.of();
        return new ProductContext(
                longValue(data.get("productId"), fallbackProductId),
                text(data.get("name"), "当前商品"),
                text(data.get("description"), "暂无商品描述"),
                decimalValue(data.get("price")),
                text(data.get("status"), "UNKNOWN"),
                integerValue(stock.get("availableStock"))
        );
    }

    static ProductContext unavailable(Long productId) {
        return new ProductContext(
                productId,
                "当前商品",
                "商品详情暂不可用",
                null,
                "UNKNOWN",
                null
        );
    }

    String promptText() {
        return "商品ID：" + productId
                + "\n商品名称：" + name
                + "\n商品描述：" + description
                + "\n商品价格：" + (price == null ? "未提供" : price.toPlainString())
                + "\n商品状态：" + status
                + "\n可用库存：" + (availableStock == null ? "未提供" : availableStock);
    }

    private static String text(Object value, String fallback) {
        String text = Objects.toString(value, "").strip();
        return text.isEmpty() ? fallback : text;
    }

    private static Long longValue(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(Objects.toString(value));
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(Objects.toString(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(Objects.toString(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
