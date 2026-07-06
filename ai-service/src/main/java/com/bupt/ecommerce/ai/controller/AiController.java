package com.bupt.ecommerce.ai.controller;

import com.bupt.ecommerce.ai.service.AiConsultService;
import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/products")
@Tag(name = "05 AI 智能导购", description = "商品 AI 咨询")
public class AiController {

    private final AiConsultService aiConsultService;

    public AiController(AiConsultService aiConsultService) {
        this.aiConsultService = aiConsultService;
    }

    @PostMapping("/{productId}/consult")
    @Operation(summary = "商品咨询", description = "CUSTOMER / ADMIN 接口，返回商品咨询回答")
    public ApiResponse<Map<String, Object>> consult(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody ConsultRequest request,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) Long userId
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return aiConsultService.consult(productId, request.question());
    }

    public record ConsultRequest(@NotBlank String question) {}
}
