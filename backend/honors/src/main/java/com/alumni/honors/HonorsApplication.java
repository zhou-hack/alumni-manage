package com.alumni.honors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友管理平台 - 校友荣誉模块（Honors）
 * 可独立运行：java -jar target/alumni-honors-0.1.0.jar
 */
@SpringBootApplication(scanBasePackages = {"com.alumni.honors", "com.alumni.common"})
public class HonorsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HonorsApplication.class, args);
    }
}
