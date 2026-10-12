package com.alumni.teachers.sync;

import java.util.List;
import java.util.Map;

/**
 * 教师数据源（内网中间接口适配层）。返回原始 Map 列表，字段 key 见 {@link TeacherFieldMapping}。
 * 实现：RestTeacherDataSource（内网 REST）/ MockTeacherDataSource（E2E 假数据）。
 * 拉取失败抛异常，由同步流程记录失败并保留本地数据（绝不清库）。
 */
public interface TeacherDataSource {

    List<Map<String, Object>> fetchTeachers();
}
