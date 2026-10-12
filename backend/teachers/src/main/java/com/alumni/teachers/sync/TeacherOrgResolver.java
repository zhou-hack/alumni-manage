package com.alumni.teachers.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组织架构解析：把内网「院系/专业」原文映射到 dicts.id（跨模块直读 dicts 表）。
 * 映射失败不阻断同步——返回的 id 为 NULL，teachers 保留原文快照兜底展示。
 * 映射规则：college 按 dicts(type=college, label) 精确匹配；major 在该院系下按 label 精确匹配。
 */
@Component
public class TeacherOrgResolver {

    private static final Logger log = LoggerFactory.getLogger(TeacherOrgResolver.class);

    private final JdbcTemplate jdbcTemplate;

    public TeacherOrgResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 解析结果（映射不到为 null） */
    public record OrgIds(Long collegeId, Long majorId) {
    }

    public OrgIds resolve(String collegeName, String majorName) {
        Long collegeId = null;
        if (collegeName != null && !collegeName.isBlank()) {
            collegeId = queryId("SELECT id FROM dicts WHERE type = 'college' AND label = ? LIMIT 1", collegeName.trim());
            if (collegeId == null) {
                log.info("院系未映射到字典：{}", collegeName);
            }
        }
        Long majorId = null;
        if (collegeId != null && majorName != null && !majorName.isBlank()) {
            majorId = queryId("SELECT id FROM dicts WHERE type = 'major' AND label = ? AND parent_id = ? LIMIT 1",
                    majorName.trim(), collegeId);
            if (majorId == null) {
                log.info("专业未映射到字典：{} / {}", collegeName, majorName);
            }
        }
        return new OrgIds(collegeId, majorId);
    }

    private Long queryId(String sql, Object... args) {
        List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, args);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
