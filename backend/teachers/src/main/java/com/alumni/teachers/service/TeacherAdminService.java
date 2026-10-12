package com.alumni.teachers.service;

import com.alumni.teachers.common.BizException;
import com.alumni.teachers.common.ErrorCode;
import com.alumni.teachers.entity.Teacher;
import com.alumni.teachers.entity.TeacherSyncLog;
import com.alumni.teachers.repository.TeacherSyncLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教师管理端查询（本期仅数据落地 + 列表展示）。
 * 列表 LEFT JOIN dicts 取院系/专业展示名，映射缺失回退原文快照（college_name/major_name）。
 */
@Service
public class TeacherAdminService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> STATUSES = Set.of(Teacher.STATUS_ACTIVE, Teacher.STATUS_DISABLED);

    private final JdbcTemplate jdbcTemplate;
    private final TeacherSyncLogRepository syncLogRepository;

    public TeacherAdminService(JdbcTemplate jdbcTemplate, TeacherSyncLogRepository syncLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncLogRepository = syncLogRepository;
    }

    /** 教师列表：college_id/major_id/status 筛选 + 工号/姓名关键字 + 分页 */
    @Transactional(readOnly = true)
    public Map<String, Object> list(Long collegeId, Long majorId, String status, String keyword,
                                    int page, int pageSize) {
        validatePage(page, pageSize);
        String effectiveStatus = (status == null || status.isBlank()) ? null : status.trim();
        if (effectiveStatus != null && !STATUSES.contains(effectiveStatus)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "status 仅支持 active / disabled");
        }
        String effectiveKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (collegeId != null) {
            where.append(" AND t.college_id = ?");
            args.add(collegeId);
        }
        if (majorId != null) {
            where.append(" AND t.major_id = ?");
            args.add(majorId);
        }
        if (effectiveStatus != null) {
            where.append(" AND t.status = ?");
            args.add(effectiveStatus);
        }
        if (effectiveKeyword != null) {
            where.append(" AND (t.teacher_no LIKE ? OR t.name LIKE ?)");
            String like = "%" + effectiveKeyword + "%";
            args.add(like);
            args.add(like);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teachers t" + where, Long.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add(pageSize);
        rowArgs.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.teacher_no, t.name, t.title, t.status,
                       t.college_id, t.major_id,
                       COALESCE(c.label, t.college_name) AS college,
                       COALESCE(m.label, t.major_name) AS major,
                       t.last_synced_at
                FROM teachers t
                LEFT JOIN dicts c ON c.id = t.college_id
                LEFT JOIN dicts m ON m.id = t.major_id
                """ + where + " ORDER BY t.teacher_no LIMIT ? OFFSET ?", rowArgs.toArray());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total == null ? 0 : total);
        data.put("page", page);
        data.put("page_size", pageSize);
        return data;
    }

    /** 同步记录分页（started_at 倒序，最新在前） */
    @Transactional(readOnly = true)
    public Map<String, Object> syncLogs(int page, int pageSize) {
        validatePage(page, pageSize);
        Page<TeacherSyncLog> records = syncLogRepository.findAllByOrderByStartedAtDescIdDesc(
                PageRequest.of(page - 1, pageSize));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.getContent());
        data.put("total", records.getTotalElements());
        data.put("page", page);
        data.put("page_size", pageSize);
        return data;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page 必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 必须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
    }
}
