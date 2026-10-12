package com.alumni.reviews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 审核模块（Reviews）
 * 可独立运行：java -jar target/alumni-reviews-0.1.0.jar
 * 服务端口：http://localhost:8082/
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.reviews", "com.alumni.common"})
public class ReviewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewsApplication.class, args);
    }
}
