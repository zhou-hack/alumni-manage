package com.alumni.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 审计模块独立调试入口（模块化单体装配时被 PlatformApplication 排除，不参与单体启动）。
 */
@SpringBootApplication
public class AuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }
}
