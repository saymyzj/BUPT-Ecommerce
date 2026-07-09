package com.bupt.ecommerce.ai.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductFaqServiceTest {

    private final ProductFaqService service = new ProductFaqService();
    private final ProductContext product = new ProductContext(
            20001L,
            "校园耳机",
            "适合日常学习和通勤",
            new BigDecimal("199.00"),
            "ACTIVE",
            12
    );

    @Test
    void priceSynonymsShouldHitSameDeterministicAnswer() {
        for (String question : new String[]{"多少钱", "价格是多少", "这款贵不贵", "售价多少"}) {
            ProductFaqService.Match match = service.classify(question, product);

            assertEquals(ProductFaqService.QuestionCategory.PRICE, match.category());
            assertTrue(match.deterministic());
            assertTrue(match.answer().contains("199"));
        }
    }

    @Test
    void stockSynonymsShouldHitStockAnswer() {
        for (String question : new String[]{"还有库存吗", "现在有货吗", "还能买吗", "剩多少件"}) {
            ProductFaqService.Match match = service.classify(question, product);

            assertEquals(ProductFaqService.QuestionCategory.STOCK, match.category());
            assertTrue(match.answer().contains("12"));
        }
    }

    @Test
    void highRiskUnknownInformationShouldNotBeInvented() {
        ProductFaqService.Match delivery = service.classify("几天能发货", product);
        ProductFaqService.Match afterSales = service.classify("支持七天无理由退货吗", product);
        ProductFaqService.Match authenticity = service.classify("保证是正品吗", product);

        assertTrue(delivery.answer().contains("无法确定"));
        assertTrue(afterSales.answer().contains("无法确定"));
        assertTrue(authenticity.answer().contains("不能替平台作出保证"));
    }

    @Test
    void recommendationQuestionShouldReturnConcreteBasis() {
        ProductFaqService.Match match = service.classify("这款耳机适合学生党购买吗？请结合价格、续航和使用场景给建议。", product);

        assertEquals(ProductFaqService.QuestionCategory.SUITABILITY, match.category());
        assertTrue(match.answer().contains("结论"));
        assertTrue(match.answer().contains("199"));
        assertTrue(match.answer().contains("适合日常学习和通勤"));
        assertTrue(match.answer().contains("12 件可用库存"));
        assertTrue(match.answer().contains("保修"));
    }

    @Test
    void unrelatedQuestionShouldFallThroughToRag() {
        ProductFaqService.Match match = service.classify("帮我总结商品特点", product);

        assertEquals(ProductFaqService.QuestionCategory.GENERAL, match.category());
        assertTrue(!match.deterministic());
    }
}
