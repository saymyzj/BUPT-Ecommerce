package com.bupt.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSeckillActivityRequest(
        @NotNull Long productId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @DecimalMin(value = "0.01") BigDecimal seckillPrice,
        @NotNull @Min(1) Integer seckillStock
) {
}
