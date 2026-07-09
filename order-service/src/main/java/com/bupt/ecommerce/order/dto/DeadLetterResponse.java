package com.bupt.ecommerce.order.dto;

import com.bupt.ecommerce.order.entity.DeadLetterRecord;

import java.time.LocalDateTime;

public record DeadLetterResponse(
        Long id,
        String messageId,
        String deathReason,
        String status,
        Integer replayCount,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DeadLetterResponse from(DeadLetterRecord record) {
        return new DeadLetterResponse(
                record.getId(),
                record.getMessageId(),
                record.getDeathReason(),
                record.getStatus().name(),
                record.getReplayCount(),
                record.getLastError(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
