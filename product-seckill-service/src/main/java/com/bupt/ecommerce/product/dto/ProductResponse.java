package com.bupt.ecommerce.product.dto;

import com.bupt.ecommerce.product.entity.Product;
import com.bupt.ecommerce.product.entity.ProductStock;

import java.math.BigDecimal;

public record ProductResponse(
        Long productId,
        String name,
        String description,
        BigDecimal price,
        String status,
        StockResponse stock
) {

    public static ProductResponse from(Product product, ProductStock stock) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                stock == null ? null : StockResponse.from(stock)
        );
    }
}
