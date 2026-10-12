package com.alumni.outstanding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 优秀校友模块独立调试入口（模块化单体装配时被 PlatformApplication 排除，不参与单体启动）。
 */
@SpringBootApplication
public class OutstandingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutstandingApplication.class, args);
    }
}
