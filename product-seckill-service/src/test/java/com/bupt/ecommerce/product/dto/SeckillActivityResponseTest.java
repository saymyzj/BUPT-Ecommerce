package com.bupt.ecommerce.product.dto;

import com.bupt.ecommerce.product.entity.SeckillActivity;
import com.bupt.ecommerce.product.entity.SeckillActivityStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeckillActivityResponseTest {

    @Test
    void readyActivityShouldDisplayOngoingDuringActivityWindow() {
        SeckillActivity activity = activity(
                SeckillActivityStatus.READY,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        assertEquals("ONGOING", SeckillActivityResponse.from(activity).status());
    }

    @Test
    void readyActivityShouldDisplayFinishedAfterActivityWindow() {
        SeckillActivity activity = activity(
                SeckillActivityStatus.READY,
                LocalDateTime.now().minusMinutes(20),
                LocalDateTime.now().minusMinutes(1)
        );

        assertEquals("FINISHED", SeckillActivityResponse.from(activity).status());
    }

    private SeckillActivity activity(SeckillActivityStatus status, LocalDateTime startTime, LocalDateTime endTime) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setProductId(20001L);
        activity.setActivityName("test");
        activity.setSeckillPrice(new BigDecimal("99.90"));
        activity.setSeckillStock(10);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setStatus(status);
        return activity;
    }
}
