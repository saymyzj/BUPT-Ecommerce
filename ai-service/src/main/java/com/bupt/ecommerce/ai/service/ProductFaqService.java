package com.bupt.ecommerce.ai.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

@Service
public class ProductFaqService {

    private static final Map<QuestionCategory, List<String>> KEYWORDS = keywordMap();

    public Match classify(String question, ProductContext product) {
        String normalized = normalize(question);
        for (Map.Entry<QuestionCategory, List<String>> entry : KEYWORDS.entrySet()) {
            Optional<String> matched = entry.getValue().stream()
                    .filter(normalized::contains)
                    .findFirst();
            if (matched.isPresent()) {
                return answer(entry.getKey(), matched.get(), product);
            }
        }
        return new Match(QuestionCategory.GENERAL, null, null, false, 0.55);
    }

    public String normalizeForCache(String question) {
        String normalized = normalize(question);
        for (Map.Entry<QuestionCategory, List<String>> entry : KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(normalized::contains)) {
                return entry.getKey().name();
            }
        }
        return normalized;
    }

    private Match answer(QuestionCategory category, String keyword, ProductContext product) {
        String answer = switch (category) {
            case PRICE -> product.price() == null
                    ? "当前商品价格信息暂不可用，请以商品详情页显示为准。"
                    : product.name() + "当前标价为 " + product.price().stripTrailingZeros().toPlainString()
                    + " 元。若参与秒杀，成交价请以秒杀活动页面为准。";
            case STOCK -> product.availableStock() == null
                    ? "当前库存信息暂不可用，请刷新商品详情后再确认。"
                    : product.name() + "当前可用库存为 " + product.availableStock()
                    + " 件。" + (product.availableStock() > 0 ? "目前仍可购买。" : "目前已无可用库存。");
            case AVAILABILITY -> "当前商品状态为 " + product.status()
                    + "。" + ("ACTIVE".equalsIgnoreCase(product.status()) ? "商品处于可售状态。" : "当前不建议继续下单。");
            case SUITABILITY -> "从现有商品信息看：" + product.description()
                    + "。是否适合你还取决于具体用途和预算；现有资料不足以确认未写明的适用人群。";
            case SECKILL -> "商品详情接口不能确认当前秒杀价格、时间或资格。请查看对应秒杀活动详情，以下单时活动状态和价格为准。";
            case DELIVERY -> "现有商品资料未提供配送范围、运费和到货时间，暂时无法确定，请以下单页或商家说明为准。";
            case AFTER_SALES -> "现有商品资料未提供退换货和保修政策，暂时无法确定，请查看订单规则或联系平台客服。";
            case AUTHENTICITY -> "现有商品资料不足以证明真伪、认证或安全资质，我不能替平台作出保证，请核验商品资质和商家凭证。";
            case GENERAL -> null;
        };
        return new Match(category, keyword, answer, true, 1.0);
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    private static Map<QuestionCategory, List<String>> keywordMap() {
        Map<QuestionCategory, List<String>> keywords = new LinkedHashMap<>();
        keywords.put(QuestionCategory.PRICE, List.of(
                "多少钱", "价格", "价钱", "售价", "贵不贵", "费用", "便宜", "学生价"
        ));
        keywords.put(QuestionCategory.STOCK, List.of(
                "库存", "有货", "现货", "缺货", "没货", "还能买", "剩多少", "多少件"
        ));
        keywords.put(QuestionCategory.AVAILABILITY, List.of(
                "上架", "下架", "可售", "能下单", "可以买", "状态"
        ));
        keywords.put(QuestionCategory.SECKILL, List.of(
                "秒杀", "抢购", "活动价", "什么时候抢", "限购"
        ));
        keywords.put(QuestionCategory.DELIVERY, List.of(
                "发货", "快递", "运费", "包邮", "配送", "几天到", "多久到"
        ));
        keywords.put(QuestionCategory.AFTER_SALES, List.of(
                "退货", "退款", "换货", "售后", "保修", "七天无理由"
        ));
        keywords.put(QuestionCategory.AUTHENTICITY, List.of(
                "正品", "真假", "安全", "认证", "质量保证", "靠谱吗"
        ));
        keywords.put(QuestionCategory.SUITABILITY, List.of(
                "适合", "学生党", "推荐", "值不值得", "能用吗", "好用吗"
        ));
        return Collections.unmodifiableMap(keywords);
    }

    public enum QuestionCategory {
        PRICE,
        STOCK,
        AVAILABILITY,
        SUITABILITY,
        SECKILL,
        DELIVERY,
        AFTER_SALES,
        AUTHENTICITY,
        GENERAL
    }

    public record Match(
            QuestionCategory category,
            String matchedKeyword,
            String answer,
            boolean deterministic,
            double confidence
    ) {}
}
