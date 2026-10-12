package com.alumni.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 认证模块（Auth）
 * 可独立运行：java -jar target/alumni-auth-0.1.0.jar
 * 测试前端：http://localhost:8080/
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.auth", "com.alumni.common"})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
