package com.alumni.wall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 留言墙模块独立调试入口（模块化单体装配时被 PlatformApplication 排除，不参与单体启动）。
 */
@SpringBootApplication
public class WallApplication {

    public static void main(String[] args) {
        SpringApplication.run(WallApplication.class, args);
    }
}
