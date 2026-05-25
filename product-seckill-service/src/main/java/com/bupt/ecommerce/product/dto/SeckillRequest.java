package com.bupt.ecommerce.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SeckillRequest(@NotNull @Min(1) Integer quantity) {
}
