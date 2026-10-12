package com.alumni.teachers.sync;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 教师同步装配：注册配置属性（数据源按 mock 条件装配，见 Rest/MockTeacherDataSource）。
 */
@Configuration
@EnableConfigurationProperties(TeacherSyncProperties.class)
public class TeacherSyncConfig {
}
