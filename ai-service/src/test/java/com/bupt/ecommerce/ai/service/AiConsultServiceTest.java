package com.bupt.ecommerce.ai.service;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConsultServiceTest {

    @Test
    void missingLlmConfigShouldReturnAiUnavailableWithAnswerOnly() {
        AiConsultService service = service("", "", redisWithoutCache(), 100);

        ApiResponse<Map<String, Object>> response = service.consult(20001L, "帮我总结商品特点");

        assertEquals(ErrorCode.AI_UNAVAILABLE.code(), response.code());
        assertEquals(ErrorCode.AI_UNAVAILABLE.message(), response.message());
        assertNotNull(response.data().get("answer"));
        assertEquals(1, response.data().size());
    }

    @Test
    void cacheHitShouldReturnSuccessAnswerOnly() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("cached answer");
        AiConsultService service = service("", "", redisTemplate, 100);

        ApiResponse<Map<String, Object>> response = service.consult(20001L, "帮我总结商品特点");

        assertEquals(ErrorCode.SUCCESS.code(), response.code());
        assertEquals("cached answer", response.data().get("answer"));
        assertEquals(1, response.data().size());
    }

    @Test
    void blankQuestionShouldReturnBadRequest() {
        AiConsultService service = service("", "", redisWithoutCache(), 100);

        ApiResponse<Map<String, Object>> response = service.consult(20001L, " ");

        assertEquals(ErrorCode.BAD_REQUEST.code(), response.code());
    }

    @Test
    void productContextAndLlmFailureShouldReturnExplicitFallback() {
        AiConsultService service = service("http://127.0.0.1:1", "test-key", redisWithoutCache(), 50);

        ApiResponse<Map<String, Object>> response = service.consult(20001L, "帮我总结商品特点");

        assertEquals(ErrorCode.AI_UNAVAILABLE.code(), response.code());
        assertNotNull(response.data().get("answer"));
        assertEquals(1, response.data().size());
    }

    private AiConsultService service(String llmBaseUrl, String llmApiKey, StringRedisTemplate redisTemplate, long timeoutMs) {
        return new AiConsultService(
                new RestTemplateBuilder(),
                redisTemplate,
                new ProductFaqService(),
                "http://127.0.0.1:1",
                llmBaseUrl,
                llmApiKey,
                "gpt-test",
                timeoutMs,
                3600
        );
    }

    private StringRedisTemplate redisWithoutCache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        return redisTemplate;
    }
}
