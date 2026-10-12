package com.alumni.directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校友档案索引模块独立调试入口（模块化单体装配时被 PlatformApplication 排除，不参与单体启动）。
 * 本模块为纯聚合检索（不建表），依赖库中 users/alumni_base/archives/showcase/consents/honors/
 * outstanding_alumni 表结构。
 */
@SpringBootApplication
public class DirectoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirectoryApplication.class, args);
    }
}
