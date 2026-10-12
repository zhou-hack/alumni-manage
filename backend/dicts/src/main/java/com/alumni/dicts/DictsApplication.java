package com.alumni.dicts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 基础字典模块（Dicts）
 * 可独立运行：java -jar target/alumni-dicts-0.1.0.jar（端口 8081）
 * 测试页：http://localhost:8081/
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.dicts", "com.alumni.common"})
public class DictsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DictsApplication.class, args);
    }
}
