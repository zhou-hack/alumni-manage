package com.alumni.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理模块（P1 [M15] 细粒度权限与账号交接）独立调试入口
 * （模块化单体装配时被 PlatformApplication 排除，不参与单体启动）。
 */
@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
