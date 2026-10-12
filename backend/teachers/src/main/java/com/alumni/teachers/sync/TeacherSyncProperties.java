package com.alumni.teachers.sync;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 教师同步配置（application.yml app.teachers.sync.*，环境变量 TEACHER_SYNC_* 覆盖）。
 * 本地无内网接口时 enabled=false 关闭定时任务；mock=true 装配内置假数据源供 E2E。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.teachers.sync")
public class TeacherSyncProperties {

    /** 定时同步总开关（false 时不装配调度器，手动同步仍可用） */
    private boolean enabled = false;

    /** 定时同步 cron（默认每日 02:00） */
    private String cron = "0 0 2 * * *";

    /** 内网教师数据 REST 接口地址 */
    private String url = "";

    /** 内网接口访问令牌（Bearer，空则不带） */
    private String token = "";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    /** true 时装配 MockTeacherDataSource（不访问内网） */
    private boolean mock = false;

    /** mock 数据源 JSON 文件路径（数组，元素为教师对象）；设置后每次拉取实时读文件，便于构造增/改/停场景 */
    private String mockFile = "";
}
