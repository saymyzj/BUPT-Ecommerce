package com.bupt.ecommerce.ai.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DigitalHumanService {

    private final List<DigitalHumanConfig> configs = List.of(
            new DigitalHumanConfig(
                    "guide_classic", "智能导购员", "classic", "female_warm",
                    "smile", "idle", "#1f7a8c", true
            ),
            new DigitalHumanConfig(
                    "guide_youth", "青春导购员", "campus", "female_bright",
                    "smile", "gentle_wave", "#4969d8", true
            ),
            new DigitalHumanConfig(
                    "guide_service", "服务助手", "service", "male_calm",
                    "neutral", "idle", "#2f855a", true
            )
    );
    private final AtomicReference<DigitalHumanConfig> activeConfig =
            new AtomicReference<>(configs.get(0));

    public DigitalHumanConfig current() {
        return activeConfig.get();
    }

    public List<DigitalHumanConfig> available() {
        return configs;
    }

    public DigitalHumanConfig select(String avatarId, String voiceId) {
        DigitalHumanConfig selected = configs.stream()
                .filter(config -> config.avatarId().equals(avatarId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown avatarId: " + avatarId));
        DigitalHumanConfig configured = voiceId == null || voiceId.isBlank()
                ? selected
                : new DigitalHumanConfig(
                        selected.avatarId(), selected.displayName(), selected.style(), voiceId.strip(),
                        selected.defaultExpression(), selected.defaultMotion(), selected.themeColor(), selected.enabled()
                );
        activeConfig.set(configured);
        return configured;
    }

    public record DigitalHumanConfig(
            String avatarId,
            String displayName,
            String style,
            String voiceId,
            String defaultExpression,
            String defaultMotion,
            String themeColor,
            boolean enabled
    ) {}
}
