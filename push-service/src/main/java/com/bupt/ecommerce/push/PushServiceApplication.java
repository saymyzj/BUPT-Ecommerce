package com.bupt.ecommerce.push;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.bupt.ecommerce")
public class PushServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PushServiceApplication.class, args);
    }
}
