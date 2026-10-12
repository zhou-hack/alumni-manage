package com.alumni.drafts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 草稿模块（Drafts）
 * 可独立运行：java -jar target/alumni-drafts-0.1.0.jar
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.drafts", "com.alumni.common"})
public class DraftsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DraftsApplication.class, args);
    }
}
