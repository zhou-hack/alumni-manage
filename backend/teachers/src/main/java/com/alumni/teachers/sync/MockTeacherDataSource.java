package com.alumni.teachers.sync;

import com.alumni.teachers.common.BizException;
import com.alumni.teachers.common.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock 教师数据源（app.teachers.sync.mock=true 时装配，本地 E2E 用，不访问内网）。
 * 配置 mock-file 指向 JSON 数组文件时每次拉取实时读文件，便于构造增/改/停场景；
 * 否则返回内置样例数据。
 */
@Component
@ConditionalOnProperty(name = "app.teachers.sync.mock", havingValue = "true")
public class MockTeacherDataSource implements TeacherDataSource {

    private static final Logger log = LoggerFactory.getLogger(MockTeacherDataSource.class);

    private final TeacherSyncProperties properties;
    private final ObjectMapper objectMapper;

    public MockTeacherDataSource(TeacherSyncProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> fetchTeachers() {
        String mockFile = properties.getMockFile();
        if (mockFile != null && !mockFile.isBlank()) {
            try {
                return objectMapper.readValue(Files.readString(Path.of(mockFile.trim())),
                        new TypeReference<>() {
                        });
            } catch (Exception e) {
                throw new BizException(ErrorCode.UPSTREAM_ERROR, "Mock 教师数据文件读取失败：" + e.getMessage());
            }
        }
        log.info("Mock 教师数据源：返回内置样例数据");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("T0001", "王建国", "数据产业学院", "大数据技术", "教授", "ext-t0001"));
        rows.add(row("T0002", "李秀英", "数据产业学院", "软件技术", "副教授", "ext-t0002"));
        rows.add(row("T0003", "张伟", "信息工程学院", "计算机应用技术", "讲师", "ext-t0003"));
        rows.add(row("T0004", "陈敏", "信息工程学院", "人工智能技术应用", "助教", "ext-t0004"));
        rows.add(row("T0005", "刘洋", "智能建造学院", "建筑工程技术", "讲师", "ext-t0005"));
        return rows;
    }

    private Map<String, Object> row(String no, String name, String college, String major,
                                    String title, String externalId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(TeacherFieldMapping.TEACHER_NO, no);
        row.put(TeacherFieldMapping.NAME, name);
        row.put(TeacherFieldMapping.COLLEGE, college);
        row.put(TeacherFieldMapping.MAJOR, major);
        row.put(TeacherFieldMapping.TITLE, title);
        row.put(TeacherFieldMapping.EXTERNAL_ID, externalId);
        return row;
    }
}
