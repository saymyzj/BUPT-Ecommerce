package com.bupt.ecommerce.order.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.order.config.RabbitMqConfig;
import com.bupt.ecommerce.order.dto.DeadLetterResponse;
import com.bupt.ecommerce.order.dto.OrderCreateMessage;
import com.bupt.ecommerce.order.entity.DeadLetterRecord;
import com.bupt.ecommerce.order.entity.DeadLetterStatus;
import com.bupt.ecommerce.order.repository.DeadLetterRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class DeadLetterService {

    private final DeadLetterRecordRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public DeadLetterService(
            DeadLetterRecordRepository repository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeadLetterRecord record(Message rabbitMessage, String payload) {
        String messageId = extractMessageId(payload);
        LocalDateTime now = LocalDateTime.now();
        DeadLetterRecord record = repository.findByMessageId(messageId).orElseGet(DeadLetterRecord::new);
        record.setMessageId(messageId);
        record.setPayload(payload);
        record.setDeathReason(limit(String.valueOf(rabbitMessage.getMessageProperties().getHeaders().get("x-death"))));
        record.setStatus(DeadLetterStatus.RECEIVED);
        record.setReplayCount(record.getReplayCount() == null ? 0 : record.getReplayCount());
        record.setCreatedAt(record.getCreatedAt() == null ? now : record.getCreatedAt());
        record.setUpdatedAt(now);
        return repository.save(record);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeadLetterResponse> page(int page, int pageSize) {
        Page<DeadLetterRecord> result = repository.findAll(
                PageRequest.of(Math.max(page, 1) - 1, Math.max(pageSize, 1))
        );
        return new PageResponse<>(
                result.getContent().stream().map(DeadLetterResponse::from).toList(),
                page,
                pageSize,
                result.getTotalElements()
        );
    }

    public DeadLetterResponse replay(Long id) {
        DeadLetterRecord record = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Message message = MessageBuilder.withBody(record.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(record.getMessageId())
                .build();
        try {
            rabbitTemplate.send(
                    RabbitMqConfig.ORDER_EXCHANGE,
                    RabbitMqConfig.ORDER_CREATE_ROUTING_KEY,
                    message
            );
            markReplayed(record.getId());
            return DeadLetterResponse.from(repository.findById(id).orElse(record));
        } catch (RuntimeException ex) {
            markReplayFailed(record.getId(), ex);
            throw new BusinessException(ErrorCode.MQ_PUBLISH_FAILED);
        }
    }

    @Transactional
    public void markResolved(String messageId) {
        repository.findByMessageId(messageId).ifPresent(record -> {
            record.setStatus(DeadLetterStatus.RESOLVED);
            record.setLastError(null);
            record.setUpdatedAt(LocalDateTime.now());
            repository.save(record);
        });
    }

    @Transactional
    public void markReplayed(Long id) {
        repository.findById(id).ifPresent(record -> {
            record.setStatus(DeadLetterStatus.REPLAYED);
            record.setReplayCount(record.getReplayCount() + 1);
            record.setLastError(null);
            record.setUpdatedAt(LocalDateTime.now());
            repository.save(record);
        });
    }

    @Transactional
    public void markReplayFailed(Long id, RuntimeException failure) {
        repository.findById(id).ifPresent(record -> {
            record.setLastError(limit(failure.getMessage()));
            record.setUpdatedAt(LocalDateTime.now());
            repository.save(record);
        });
    }

    private String extractMessageId(String payload) {
        try {
            OrderCreateMessage message = objectMapper.readValue(payload, OrderCreateMessage.class);
            if (message.messageId() != null && !message.messageId().isBlank()) {
                return message.messageId();
            }
        } catch (Exception ignored) {
            // Malformed messages still need a stable identity for deduplication and inspection.
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return "malformed-" + HexFormat.of().formatHex(digest, 0, 24);
        } catch (Exception ex) {
            return "malformed-" + Integer.toHexString(payload.hashCode());
        }
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
