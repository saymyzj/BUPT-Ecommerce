package com.bupt.ecommerce.ai.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AudioCatalogService {

    private final ResourceLoader resourceLoader;

    public AudioCatalogService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public AudioMatch resolve(ProductFaqService.QuestionCategory category) {
        AudioItem item = catalog().stream()
                .filter(candidate -> candidate.category().equals(category.name()))
                .findFirst()
                .orElse(null);
        if (item == null) {
            return new AudioMatch(null, "NOT_CONFIGURED", false);
        }
        Resource resource = resourceLoader.getResource("classpath:/static" + item.audioUrl());
        return resource.exists()
                ? new AudioMatch(item.audioUrl(), "READY", true)
                : new AudioMatch(null, "NOT_CONFIGURED", false);
    }

    public List<AudioItem> catalog() {
        return List.of(
                new AudioItem("PRICE", "商品价格说明", "/media/tts/product_price.mp3", true),
                new AudioItem("STOCK", "商品库存说明", "/media/tts/product_stock.mp3", true),
                new AudioItem("SECKILL", "秒杀规则说明", "/media/tts/seckill_rule.mp3", true),
                new AudioItem("DELIVERY", "配送信息兜底", "/media/tts/delivery_unknown.mp3", false),
                new AudioItem("AFTER_SALES", "售后政策兜底", "/media/tts/after_sales_unknown.mp3", false),
                new AudioItem("AUTHENTICITY", "资质核验提醒", "/media/tts/authenticity_warning.mp3", false),
                new AudioItem("GENERAL", "咨询服务兜底", "/media/tts/fallback_unknown.mp3", true)
        );
    }

    public record AudioItem(String category, String description, String audioUrl, boolean requiredForDemo) {}

    public record AudioMatch(String audioUrl, String audioStatus, boolean audioCached) {}
}
