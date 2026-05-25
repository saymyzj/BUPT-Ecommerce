package com.bupt.ecommerce.product.dto;

import com.bupt.ecommerce.product.entity.ProductStock;

public record StockResponse(
        Long productId,
        Integer totalStock,
        Integer availableStock,
        Integer reservedStock
) {

    public static StockResponse from(ProductStock stock) {
        return new StockResponse(
                stock.getProductId(),
                stock.getTotalStock(),
                stock.getAvailableStock(),
                stock.getReservedStock()
        );
    }
}
