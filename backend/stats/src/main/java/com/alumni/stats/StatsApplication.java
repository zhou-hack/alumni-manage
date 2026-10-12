package com.alumni.stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 统计模块（Stats）
 * 可独立运行：java -jar target/alumni-stats-0.1.0.jar
 * 服务端口：http://localhost:8081/
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.stats", "com.alumni.common"})
public class StatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatsApplication.class, args);
    }
}
