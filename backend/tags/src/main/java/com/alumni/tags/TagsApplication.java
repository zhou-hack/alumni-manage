package com.alumni.tags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 标签与检索模块独立调试入口（模块化单体装配时被 PlatformApplication 排除，不参与单体启动）。
 */
@SpringBootApplication
public class TagsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TagsApplication.class, args);
    }
}
