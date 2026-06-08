package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.product.dto.CreateSeckillActivityRequest;
import com.bupt.ecommerce.product.service.SeckillService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SeckillControllerTest {

    private final SeckillController controller = new SeckillController(mock(SeckillService.class), 10001L, false);

    @Test
    void createActivityShouldRejectNonAdminRoleInsideService() {
        CreateSeckillActivityRequest request = new CreateSeckillActivityRequest(
                1L,
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now().plusMinutes(10),
                new BigDecimal("99.90"),
                10
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.create(request, "CUSTOMER"));

        assertEquals(ErrorCode.FORBIDDEN.code(), ex.getCode());
    }
}
