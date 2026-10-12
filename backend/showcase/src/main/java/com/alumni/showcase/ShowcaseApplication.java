package com.alumni.showcase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 展览馆/大屏模块（Showcase）
 * 可独立运行：java -jar target/alumni-showcase-0.1.0.jar
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.showcase", "com.alumni.common"})
public class ShowcaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShowcaseApplication.class, args);
    }
}
