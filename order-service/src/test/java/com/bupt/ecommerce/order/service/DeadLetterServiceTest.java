package com.bupt.ecommerce.order.service;

import com.bupt.ecommerce.order.entity.DeadLetterRecord;
import com.bupt.ecommerce.order.entity.DeadLetterStatus;
import com.bupt.ecommerce.order.repository.DeadLetterRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class DeadLetterServiceTest {

    @Test
    void malformedDeadLetterShouldReceiveStableInspectableIdentity() {
        DeadLetterRecordRepository repository = mock(DeadLetterRecordRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        DeadLetterService service = new DeadLetterService(repository, rabbitTemplate, new ObjectMapper());
        Message message = MessageBuilder.withBody("not-json".getBytes(StandardCharsets.UTF_8)).build();
        when(repository.findByMessageId(any())).thenReturn(Optional.empty());
        when(repository.save(any(DeadLetterRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeadLetterRecord record = service.record(message, "not-json");

        assertTrue(record.getMessageId().startsWith("malformed-"));
        assertEquals(DeadLetterStatus.RECEIVED, record.getStatus());
        assertEquals(0, record.getReplayCount());
    }

    @Test
    void replayShouldUseExistingExchangeContractAndMarkRecordReplayed() {
        DeadLetterRecordRepository repository = mock(DeadLetterRecordRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        DeadLetterService service = new DeadLetterService(repository, rabbitTemplate, new ObjectMapper());
        DeadLetterRecord record = new DeadLetterRecord();
        record.setId(1L);
        record.setMessageId("message-1");
        record.setPayload("{}");
        record.setStatus(DeadLetterStatus.RECEIVED);
        record.setReplayCount(0);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(repository.save(any(DeadLetterRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                any(Message.class),
                any(CorrelationData.class)
        );

        service.replay(1L);

        verify(rabbitTemplate).send(
                org.mockito.ArgumentMatchers.eq("seckill.order.exchange"),
                org.mockito.ArgumentMatchers.eq("seckill.order.create"),
                any(Message.class),
                any(CorrelationData.class)
        );
        assertEquals(DeadLetterStatus.REPLAYED, record.getStatus());
        assertEquals(1, record.getReplayCount());
    }
}
