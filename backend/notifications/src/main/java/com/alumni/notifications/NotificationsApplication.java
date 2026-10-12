package com.alumni.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 消息通知模块独立调试入口（模块化单体装配时被 PlatformApplication 排除，不参与单体启动）。
 */
@SpringBootApplication
public class NotificationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationsApplication.class, args);
    }
}
