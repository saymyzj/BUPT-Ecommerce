package com.bupt.ecommerce.product.dto;

import com.bupt.ecommerce.product.entity.SeckillPublishEvent;

import java.time.LocalDateTime;

public record PublishEventResponse(
        Long id,
        String messageId,
        String status,
        Integer retryCount,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime updatedAt
) {
    public static PublishEventResponse from(SeckillPublishEvent event) {
        return new PublishEventResponse(
                event.getId(),
                event.getMessageId(),
                event.getStatus().name(),
                event.getRetryCount(),
                event.getLastError(),
                event.getNextRetryAt(),
                event.getUpdatedAt()
        );
    }
}
