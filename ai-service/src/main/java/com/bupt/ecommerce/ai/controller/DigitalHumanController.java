package com.bupt.ecommerce.ai.controller;

import com.bupt.ecommerce.ai.service.AudioCatalogService;
import com.bupt.ecommerce.ai.service.DigitalHumanService;
import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.common.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "06 数字人与语音配置", description = "数字人形象、声音及预录音频清单")
public class DigitalHumanController {

    private final DigitalHumanService digitalHumanService;
    private final AudioCatalogService audioCatalogService;

    public DigitalHumanController(
            DigitalHumanService digitalHumanService,
            AudioCatalogService audioCatalogService
    ) {
        this.digitalHumanService = digitalHumanService;
        this.audioCatalogService = audioCatalogService;
    }

    @GetMapping("/digital-human/config")
    @Operation(summary = "数字人配置", description = "返回当前形象及可选形象")
    public ApiResponse<DigitalHumanConfigResponse> digitalHumanConfig() {
        return ApiResponse.success(new DigitalHumanConfigResponse(
                digitalHumanService.current(),
                digitalHumanService.available()
        ));
    }

    @PutMapping("/digital-human/config")
    @Operation(summary = "切换数字人配置", description = "ADMIN 接口，切换形象和默认声音")
    public ApiResponse<DigitalHumanService.DigitalHumanConfig> updateDigitalHumanConfig(
            @Valid @RequestBody DigitalHumanConfigRequest request,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        if (!Role.ADMIN.name().equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ApiResponse.success(digitalHumanService.select(request.avatarId(), request.voiceId()));
    }

    @GetMapping("/audio/catalog")
    @Operation(summary = "高频预录语音清单", description = "返回待配置的预录音频文件及演示优先级")
    public ApiResponse<List<AudioCatalogService.AudioItem>> audioCatalog() {
        return ApiResponse.success(audioCatalogService.catalog());
    }

    public record DigitalHumanConfigRequest(@NotBlank String avatarId, String voiceId) {}

    public record DigitalHumanConfigResponse(
            DigitalHumanService.DigitalHumanConfig current,
            List<DigitalHumanService.DigitalHumanConfig> available
    ) {}
}
