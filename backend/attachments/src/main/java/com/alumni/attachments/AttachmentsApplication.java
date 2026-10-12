package com.alumni.attachments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校友管理平台 - 附件模块（Attachments，跨模块复用组件）
 * 可独立运行：java -jar target/alumni-attachments-0.1.0.jar（端口 8082）
 * 测试页：http://localhost:8082/
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.alumni.attachments", "com.alumni.common"})
public class AttachmentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttachmentsApplication.class, args);
    }
}
