package com.bupt.ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.bupt.ecommerce")
@EnableScheduling
public class ProductSeckillServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductSeckillServiceApplication.class, args);
    }
}
