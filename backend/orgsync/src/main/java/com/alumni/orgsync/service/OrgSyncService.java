package com.alumni.orgsync.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.orgsync.common.BizException;
import com.alumni.orgsync.common.ErrorCode;
import com.alumni.orgsync.dto.OrgSyncDtos;
import com.alumni.orgsync.dto.OrgSyncDtos.LogItem;
import com.alumni.orgsync.dto.OrgSyncDtos.PageResult;
import com.alumni.orgsync.dto.OrgSyncDtos.SyncRequest;
import com.alumni.orgsync.dto.OrgSyncDtos.SyncResult;
import com.alumni.orgsync.entity.OrgSyncLog;
import com.alumni.orgsync.repository.OrgSyncLogRepository;
import com.alumni.orgsync.spi.AlumniBaseDataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织架构同步 Service（手动触发）：
 * <ul>
 *   <li>sync：调用 OrganizationDataSource 拉快照 → 写入 / 更新 users / alumni_base → 写 org_sync_log + audit_logs</li>
 *   <li>listLog：分页查同步日志</li>
 * </ul>
 *
 * <p>权限：仅 school_admin / system_admin；操作必写 audit（action=org_sync）。</p>
 *
 * <p>本期 MVP：仅 alumni_base（学号/姓名/手机/学院/专业/年级）单向同步到 users 表。</p>
 */
@Service
public class OrgSyncService {

    private static final Logger log = LoggerFactory.getLogger(OrgSyncService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("school_admin", "system_admin");

    private final OrgSyncLogRepository logRepo;
    private final AlumniBaseDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OrgSyncService(OrgSyncLogRepository logRepo,
                          AlumniBaseDataSource dataSource,
                          JdbcTemplate jdbcTemplate,
                          ObjectMapper objectMapper) {
        this.logRepo = logRepo;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 触发同步（同步执行；典型 1000 行 < 5s） */
    @Transactional
    public SyncResult sync(AuthPrincipal operator, SyncRequest req) {
        requireManager(operator);
        if (!"alumni_base".equals(req.syncType())) {
            // 预留 users/dicts 扩展位；当前未实现
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "当前仅支持同步 alumni_base 类型（users/dicts 暂未实现）");
        }

        OrgSyncLog rec = new OrgSyncLog();
        rec.setSource(dataSource.name());
        rec.setSyncType(req.syncType());
        rec.setMode(req.mode() == null ? "incremental" : req.mode());
        rec.setOperatorId(operator.userId());
        rec.setStartedAt(Instant.now());
        rec.setStatus("running");
        rec.setRemark(req.remark());
        rec = logRepo.save(rec);

        int inserted = 0, updated = 0, skipped = 0, failed = 0;
        List<Map<String, Object>> failedRows = new ArrayList<>();

        try {
            List<Map<String, Object>> rows = dataSource.fetchAll(req.syncType());
            rec.setTotal(rows.size());
            for (Map<String, Object> row : rows) {
                try {
                    UpsertOutcome outcome = upsertAlumniBase(row);
                    switch (outcome) {
                        case INSERTED -> inserted++;
                        case UPDATED -> updated++;
                        case SKIPPED -> skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    Map<String, Object> fail = new java.util.LinkedHashMap<>();
                    fail.put("ext_id", row.get("ext_id"));
                    fail.put("student_id", row.get("student_id"));
                    fail.put("reason", e.getMessage());
                    failedRows.add(fail);
                }
            }
            rec.setInserted(inserted);
            rec.setUpdated(updated);
            rec.setSkipped(skipped);
            rec.setFailed(failed);
            rec.setFailures(toJson(failedRows));
            rec.setFinishedAt(Instant.now());
            rec.setStatus(failed == 0 ? "success" : (inserted + updated > 0 ? "partial" : "failed"));
            logRepo.save(rec);

            writeAudit(operator, rec.getId(), Map.of(
                    "sync_type", req.syncType(),
                    "total", rows.size(),
                    "inserted", inserted,
                    "updated", updated,
                    "skipped", skipped,
                    "failed", failed,
                    "status", rec.getStatus()));

            return new SyncResult(rec.getId(), rec.getStatus(), rows.size(), inserted, updated, skipped, failed);
        } catch (Exception e) {
            log.error("组织架构同步失败", e);
            rec.setFinishedAt(Instant.now());
            rec.setStatus("failed");
            rec.setFailures(toJson(List.of(Map.of("reason", e.getMessage()))));
            logRepo.save(rec);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "同步异常：" + e.getMessage());
        }
    }

    /** 同步日志列表 */
    @Transactional(readOnly = true)
    public PageResult<LogItem> listLog(AuthPrincipal operator, int page, int pageSize) {
        requireManager(operator);
        if (pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 须在 1~100");
        }
        Page<OrgSyncLog> result = logRepo.findAllByOrderByIdDesc(PageRequest.of(Math.max(page - 1, 0), pageSize));
        List<LogItem> list = result.getContent().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotalElements(), page, pageSize);
    }

    /* ---------------- 私有 ---------------- */

    private enum UpsertOutcome { INSERTED, UPDATED, SKIPPED }

    /**
     * 把 alumni_base 行 upsert 到 users 表。
     * 策略：以 student_id 为匹配键；若 users 已存在则比较 real_name/phone/college/major/grad_year
     * 是否有差异决定 update 还是 skip；不存在则 insert。
     */
    private UpsertOutcome upsertAlumniBase(Map<String, Object> row) {
        String studentId = (String) row.get("student_id");
        String realName = (String) row.get("real_name");
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("student_id 不能为空");
        }
        Long extId = row.get("ext_id") == null ? null : ((Number) row.get("ext_id")).longValue();
        Object gradYearObj = row.get("grad_year");
        Integer gradYear = gradYearObj == null ? null : ((Number) gradYearObj).intValue();
        String phone = (String) row.get("phone");
        String college = (String) row.get("college");
        String major = (String) row.get("major");

        // 查询 users 当前快照（按 student_id）
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT u.id, u.phone, u.nickname, a.college, a.major, a.grad_year " +
                "FROM users u LEFT JOIN alumni_base a ON u.alumni_base_id = a.id " +
                "WHERE u.student_id = ?", studentId);

        if (existing.isEmpty()) {
            // 1. 插入 alumni_base
            jdbcTemplate.update(
                    "INSERT INTO alumni_base (student_id, real_name, phone, college, major, grad_year) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    studentId, realName, phone, college, major, gradYear);
            Long baseId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            // 2. 插入 users（默认 alumni 角色）
            jdbcTemplate.update(
                    "INSERT INTO users (uuid, student_id, nickname, phone, role, status, alumni_base_id) " +
                    "VALUES (UUID(), ?, ?, ?, 'alumni', 'active', ?)",
                    studentId, realName, phone, baseId);
            return UpsertOutcome.INSERTED;
        }

        Map<String, Object> cur = existing.get(0);
        Long userId = ((Number) cur.get("id")).longValue();
        // 比对关键字段，无变化 → skip
        boolean same = eq(phone, cur.get("phone"))
                && eq(realName, cur.get("nickname"))
                && eq(college, cur.get("college"))
                && eq(major, cur.get("major"))
                && eq(gradYear, cur.get("grad_year"));
        if (same) return UpsertOutcome.SKIPPED;

        // 更新 alumni_base + users
        if (extId != null) {
            jdbcTemplate.update(
                    "UPDATE alumni_base SET real_name = ?, phone = ?, college = ?, major = ?, grad_year = ? WHERE id = ?",
                    realName, phone, college, major, gradYear, extId);
        } else {
            jdbcTemplate.update(
                    "UPDATE alumni_base SET real_name = ?, phone = ?, college = ?, major = ?, grad_year = ? " +
                    "WHERE student_id = ?",
                    realName, phone, college, major, gradYear, studentId);
        }
        jdbcTemplate.update(
                "UPDATE users SET nickname = ?, phone = ? WHERE id = ?",
                realName, phone, userId);
        return UpsertOutcome.UPDATED;
    }

    private static boolean eq(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private LogItem toItem(OrgSyncLog r) {
        return new LogItem(r.getId(), r.getSource(), r.getSyncType(), r.getMode(),
                r.getTotal(), r.getInserted(), r.getUpdated(), r.getSkipped(), r.getFailed(),
                r.getStatus(), r.getRemark(), r.getOperatorId(), r.getStartedAt(), r.getFinishedAt());
    }

    private void writeAudit(AuthPrincipal op, Long bizId, Map<String, Object> detail) {
        try {
            String json = objectMapper.writeValueAsString(detail);
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail) " +
                    "VALUES (?, ?, 'org_sync', 'org_sync', ?, ?)",
                    op.userId(), op.role(), bizId, json);
        } catch (Exception e) {
            log.error("写组织同步审计失败", e);
        }
    }

    private void requireManager(AuthPrincipal op) {
        if (op == null) throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        if (!ALLOWED_ROLES.contains(op.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅校级 / 系统管理员可同步组织架构");
        }
    }
}
