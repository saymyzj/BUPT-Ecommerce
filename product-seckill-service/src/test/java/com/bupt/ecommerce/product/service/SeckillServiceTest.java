package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.product.dto.SeckillRequest;
import com.bupt.ecommerce.product.entity.SeckillActivity;
import com.bupt.ecommerce.product.entity.SeckillActivityStatus;
import com.bupt.ecommerce.product.mq.OrderMessagePublisher;
import com.bupt.ecommerce.product.redis.SeckillRedisKeys;
import com.bupt.ecommerce.product.repository.ProductRepository;
import com.bupt.ecommerce.product.repository.ProductStockRepository;
import com.bupt.ecommerce.product.repository.SeckillActivityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SeckillServiceTest {

    private SeckillActivityRepository activityRepository;
    private StringRedisTemplate redisTemplate;
    private OrderMessagePublisher orderMessagePublisher;
    private SeckillService seckillService;

    @BeforeEach
    void setUp() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductStockRepository stockRepository = mock(ProductStockRepository.class);
        activityRepository = mock(SeckillActivityRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        orderMessagePublisher = mock(OrderMessagePublisher.class);
        seckillService = new SeckillService(
                productRepository,
                stockRepository,
                activityRepository,
                redisTemplate,
                orderMessagePublisher,
                new ObjectMapper(),
                "http://localhost:8083",
                "internal-token",
                3600,
                86400
        );

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity()));
        when(redisTemplate.hasKey(SeckillRedisKeys.stock(1L))).thenReturn(true);
        when(redisTemplate.hasKey(SeckillRedisKeys.activity(1L))).thenReturn(true);
    }

    @ParameterizedTest
    @CsvSource({
            "1,10001",
            "2,10004",
            "3,10002",
            "4,10003",
            "5,404"
    })
    void seckillShouldMapLuaReturnCodeToBusinessError(long luaResult, int expectedCode) {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(luaResult);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.seckill(1L, 10001L, new SeckillRequest(1)));

        assertEquals(expectedCode, ex.getCode());
        verifyNoInteractions(orderMessagePublisher);
    }

    @Test
    void duplicatedSeckillShouldReturnDuplicatedErrorCode() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.seckill(1L, 10001L, new SeckillRequest(1)));

        assertEquals(ErrorCode.SECKILL_DUPLICATED.code(), ex.getCode());
    }

    @Test
    void stockNotEnoughShouldReturnStockErrorCode() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillService.seckill(1L, 10001L, new SeckillRequest(1)));

        assertEquals(ErrorCode.STOCK_NOT_ENOUGH.code(), ex.getCode());
    }

    private SeckillActivity activity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setProductId(20001L);
        activity.setActivityName("test");
        activity.setSeckillPrice(new BigDecimal("99.90"));
        activity.setSeckillStock(10);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusMinutes(10));
        activity.setStatus(SeckillActivityStatus.READY);
        activity.setCreatedAt(LocalDateTime.now());
        activity.setUpdatedAt(LocalDateTime.now());
        return activity;
    }
}
