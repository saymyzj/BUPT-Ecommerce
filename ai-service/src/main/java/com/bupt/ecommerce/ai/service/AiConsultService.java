package com.bupt.ecommerce.ai.service;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
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
    private final StringRedisTemplate redisTemplate;
    private final ProductFaqService productFaqService;
    private final Duration cacheTtl;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public AiConsultService(
            RestTemplateBuilder restTemplateBuilder,
            StringRedisTemplate redisTemplate,
            ProductFaqService productFaqService,
            @Value("${app.product-service.base-url:http://localhost:8082}") String productServiceBaseUrl,
            @Value("${app.llm.base-url:}") String llmBaseUrl,
            @Value("${app.llm.api-key:}") String llmApiKey,
            @Value("${app.llm.model:gpt-4o-mini}") String llmModel,
            @Value("${app.llm.timeout-ms:8000}") long timeoutMs,
            @Value("${app.ai.cache.ttl-seconds:3600}") long cacheTtlSeconds
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.redisTemplate = redisTemplate;
        this.productFaqService = productFaqService;
        this.productServiceBaseUrl = productServiceBaseUrl;
        this.llmBaseUrl = llmBaseUrl == null ? "" : llmBaseUrl.strip();
        this.llmApiKey = llmApiKey == null ? "" : llmApiKey.strip();
        this.llmModel = llmModel;
        this.cacheTtl = Duration.ofSeconds(Math.max(cacheTtlSeconds, 60));
    }

    public ApiResponse<Map<String, Object>> consult(Long productId, String question) {
        if (question == null || question.isBlank()) {
            return ApiResponse.failure(ErrorCode.BAD_REQUEST.code(), ErrorCode.BAD_REQUEST.message());
        }

        String normalizedQuestion = question.strip();
        ProductContext productContext = loadProductContext(productId);
        ProductFaqService.Match faqMatch = productFaqService.classify(normalizedQuestion, productContext);
        String cacheKey = cacheKey(productId, productContext.promptText(), productFaqService.normalizeForCache(normalizedQuestion));
        String cached = cachedAnswer(cacheKey);
        if (cached != null) {
            return ApiResponse.success(Map.of("answer", cached));
        }
        if (faqMatch.deterministic()) {
            cacheAnswer(cacheKey, faqMatch.answer());
            return ApiResponse.success(Map.of("answer", faqMatch.answer()));
        }

        if (llmBaseUrl.isBlank() || llmApiKey.isBlank()) {
            return fallback();
        }

        try {
            String answer = requestLlm(productContext.promptText(), normalizedQuestion);
            cacheAnswer(cacheKey, answer);
            return ApiResponse.success(Map.of("answer", answer));
        } catch (RuntimeException ex) {
            log.warn("ai consult fallback productId={} reason={}", productId, ex.getMessage());
            return fallback();
        }
    }

    @SuppressWarnings("unchecked")
    private ProductContext loadProductContext(Long productId) {
        try {
            ApiResponse<?> response = restTemplate.getForObject(
                    productServiceBaseUrl + "/api/products/" + productId,
                    ApiResponse.class
            );
            Object data = response == null ? null : response.data();
            if (data instanceof Map<?, ?> map) {
                return ProductContext.from((Map<String, Object>) map, productId);
            }
            return ProductContext.unavailable(productId);
        } catch (RestClientException ex) {
            return ProductContext.unavailable(productId);
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
                                        + "不得编造库存、销量、物流、优惠、紧迫性或平台承诺。"
                                        + "只有上下文明确提供库存时才能引用库存数字；否则必须说库存未提供。"
                                        + "禁止使用“仅剩”“马上抢”“现货紧张”等未由上下文证明的表达。"
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

    private String cachedAnswer(String cacheKey) {
        String localAnswer = cache.get(cacheKey);
        if (localAnswer != null) {
            return localAnswer;
        }
        try {
            String redisAnswer = redisTemplate.opsForValue().get(cacheKey);
            if (redisAnswer != null) {
                cache.put(cacheKey, redisAnswer);
                return redisAnswer;
            }
        } catch (RuntimeException ex) {
            log.warn("ai redis cache read failed key={} reason={}", cacheKey, ex.getMessage());
        }
        return null;
    }

    private void cacheAnswer(String cacheKey, String answer) {
        cache.put(cacheKey, answer);
        try {
            redisTemplate.opsForValue().set(cacheKey, answer, cacheTtl);
        } catch (RuntimeException ex) {
            log.warn("ai redis cache write failed key={} reason={}", cacheKey, ex.getMessage());
        }
    }

    private String cacheKey(Long productId, String productContext, String question) {
        return "ai:product:qa:v2:" + productId + ":" + sha256(productContext + "\n" + question.toLowerCase());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private ApiResponse<Map<String, Object>> fallback() {
        return new ApiResponse<>(ErrorCode.AI_UNAVAILABLE.code(), ErrorCode.AI_UNAVAILABLE.message(), Map.of(
                "answer", FALLBACK_ANSWER
        ));
    }
}
