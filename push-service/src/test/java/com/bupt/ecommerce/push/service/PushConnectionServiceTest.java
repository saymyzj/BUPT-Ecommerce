package com.bupt.ecommerce.push.service;

import com.bupt.ecommerce.push.dto.OrderPushEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushConnectionServiceTest {

    @Test
    void publishShouldOnlyDeliverToSubscribedUser() {
        PushConnectionService service = new PushConnectionService();
        service.subscribe(10001L);
        service.subscribe(10002L);
        service.subscribe(10001L);

        int delivered = service.publish(new OrderPushEvent(
                "ORDER_CREATED",
                1L,
                10L,
                "SO202606080001",
                10001L,
                "CREATED",
                "order created"
        ));

        assertEquals(2, delivered);
    }
}
