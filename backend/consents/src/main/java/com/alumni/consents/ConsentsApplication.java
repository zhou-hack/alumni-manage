package com.alumni.consents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 隐私授权模块（Consents）
 * 可独立运行：java -jar target/alumni-consents-0.1.0.jar
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.consents", "com.alumni.common"})
public class ConsentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsentsApplication.class, args);
    }
}
