package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.product.dto.CreateProductRequest;
import com.bupt.ecommerce.product.dto.SetStockRequest;
import com.bupt.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProductControllerTest {

    private final ProductController controller = new ProductController(mock(ProductService.class), 1L, false);

    @Test
    void createShouldRejectNonAdminRoleInsideService() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.create(new CreateProductRequest("phone", new BigDecimal("1999.00"), "demo"), 1L, "CUSTOMER"));

        assertEquals(ErrorCode.FORBIDDEN.code(), ex.getCode());
    }

    @Test
    void setStockShouldRejectMissingAdminRoleInsideService() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.stock(1L, new SetStockRequest(10), null));

        assertEquals(ErrorCode.FORBIDDEN.code(), ex.getCode());
    }
}
