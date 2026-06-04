package com.bupt.ecommerce.order.push;

import com.bupt.ecommerce.order.dto.OrderPushEvent;
import com.bupt.ecommerce.order.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class OrderPushClient {

    private static final Logger log = LoggerFactory.getLogger(OrderPushClient.class);
    private static final String ORDER_CREATED = "ORDER_CREATED";

    private final RestTemplate restTemplate;
    private final String publishUrl;

    public OrderPushClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.push-service.base-url:http://localhost:8085}") String pushServiceBaseUrl
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.publishUrl = pushServiceBaseUrl + "/api/push/orders/internal/events";
    }

    public void publishCreated(Order order) {
        OrderPushEvent event = new OrderPushEvent(
                ORDER_CREATED,
                order.getActivityId(),
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus().name(),
                "订单创建成功"
        );
        try {
            restTemplate.postForEntity(publishUrl, event, String.class);
            log.info("published order push event orderId={} userId={}", order.getId(), order.getUserId());
        } catch (RestClientException ex) {
            log.warn("failed to publish order push event orderId={} userId={}: {}",
                    order.getId(), order.getUserId(), ex.getMessage());
        }
    }
}
