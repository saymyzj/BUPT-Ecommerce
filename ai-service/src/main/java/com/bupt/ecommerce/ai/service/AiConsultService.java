package com.bupt.ecommerce.ai.service;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiConsultService {

    private static final Logger log = LoggerFactory.getLogger(AiConsultService.class);
    private static final String FALLBACK_ANSWER = "当前咨询服务繁忙，建议先查看商品详情页信息。";

    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;
    private final String llmBaseUrl;
    private final String llmApiKey;
    private final String llmModel;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public AiConsultService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.product-service.base-url:http://localhost:8082}") String productServiceBaseUrl,
            @Value("${app.llm.base-url:}") String llmBaseUrl,
            @Value("${app.llm.api-key:}") String llmApiKey,
            @Value("${app.llm.model:gpt-4o-mini}") String llmModel,
            @Value("${app.llm.timeout-ms:8000}") long timeoutMs
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.productServiceBaseUrl = productServiceBaseUrl;
        this.llmBaseUrl = llmBaseUrl == null ? "" : llmBaseUrl.strip();
        this.llmApiKey = llmApiKey == null ? "" : llmApiKey.strip();
        this.llmModel = llmModel;
    }

    public ApiResponse<Map<String, Object>> consult(Long productId, String question) {
        if (question == null || question.isBlank()) {
            return ApiResponse.failure(ErrorCode.BAD_REQUEST.code(), ErrorCode.BAD_REQUEST.message());
        }

        String cacheKey = productId + ":" + question.strip().toLowerCase();
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return ApiResponse.success(Map.of(
                    "answer", cached,
                    "status", "SUCCESS",
                    "cacheHit", true
            ));
        }

        if (llmBaseUrl.isBlank() || llmApiKey.isBlank()) {
            return fallback(productId, question, true);
        }

        try {
            String productContext = loadProductContext(productId);
            String answer = requestLlm(productContext, question.strip());
            cache.put(cacheKey, answer);
            return ApiResponse.success(Map.of(
                    "answer", answer,
                    "status", "SUCCESS",
                    "cacheHit", false
            ));
        } catch (RuntimeException ex) {
            log.warn("ai consult fallback productId={} reason={}", productId, ex.getMessage());
            return fallback(productId, question, false);
        }
    }

    @SuppressWarnings("unchecked")
    private String loadProductContext(Long productId) {
        try {
            ApiResponse<?> response = restTemplate.getForObject(
                    productServiceBaseUrl + "/api/products/" + productId,
                    ApiResponse.class
            );
            Object data = response == null ? null : response.data();
            return Objects.toString(data, "商品信息暂不可用");
        } catch (RestClientException ex) {
            return "商品信息暂不可用，productId=" + productId;
        }
    }

    @SuppressWarnings("unchecked")
    private String requestLlm(String productContext, String question) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        Map<String, Object> request = Map.of(
                "model", llmModel,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "你是电商商品导购助手。只能基于给定商品上下文回答，回答要简洁、可执行。"
                        ),
                        Map.of(
                                "role", "user",
                                "content", "商品上下文：" + productContext + "\n用户问题：" + question
                        )
                )
        );

        Map<String, Object> response = restTemplate.postForObject(
                chatCompletionsUrl(),
                new HttpEntity<>(request, headers),
                Map.class
        );
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("llm response has no choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = message == null ? null : Objects.toString(message.get("content"), "").strip();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("llm response content is empty");
        }
        return content;
    }

    private String chatCompletionsUrl() {
        if (llmBaseUrl.endsWith("/chat/completions")) {
            return llmBaseUrl;
        }
        String baseUrl = llmBaseUrl.endsWith("/") ? llmBaseUrl.substring(0, llmBaseUrl.length() - 1) : llmBaseUrl;
        return baseUrl + "/v1/chat/completions";
    }

    private ApiResponse<Map<String, Object>> fallback(Long productId, String question, boolean missingConfig) {
        return new ApiResponse<>(ErrorCode.AI_UNAVAILABLE.code(), ErrorCode.AI_UNAVAILABLE.message(), Map.of(
                "answer", FALLBACK_ANSWER,
                "status", "FALLBACK",
                "cacheHit", false,
                "productId", productId,
                "question", question,
                "reason", missingConfig ? "LLM_NOT_CONFIGURED" : "LLM_CALL_FAILED"
        ));
    }
}
