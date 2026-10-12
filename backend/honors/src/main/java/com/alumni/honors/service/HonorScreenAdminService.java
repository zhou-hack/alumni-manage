package com.alumni.honors.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.common.BizException;
import com.alumni.honors.common.ErrorCode;
import com.alumni.honors.dto.HonorAdminDtos.BatchScreenStatusRequest;
import com.alumni.honors.dto.HonorAdminDtos.ScreenLogItem;
import com.alumni.honors.dto.HonorAdminDtos.ScreenLogPage;
import com.alumni.honors.dto.HonorAdminDtos.ScreenStatusResult;
import com.alumni.honors.dto.HonorAdminDtos.SetScreenStatusRequest;
import com.alumni.honors.entity.Honor;
import com.alumni.honors.entity.HonorScreenLog;
import com.alumni.honors.repository.HonorRepository;
import com.alumni.honors.repository.HonorScreenLogRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [P1/M17] 荣誉大屏上下架管理。
 * - 单条上下架：POST /admin/honors/{id}/screen-status
 * - 批量：POST /admin/honors/screen-status:batch
 * - 日志分页：GET /admin/honors/screen-logs
 *
 * <p>所有 screen_status 变更都经过 honor_screen_log 审计：
 *   - before/after 状态必须不同才落库（避免脏写）
 *   - 失败原因按 honor 维度收集，skipped 计「已是目标态」的条目
 *   - college_admin 限定本院；不允许跨院上下架</p>
 */
@Service
public class HonorScreenAdminService {

    private static final Set<String> SCREEN_STATUSES = Set.of("online", "offline");
    private static final Set<String> ADMIN_ROLES = Set.of("college_admin", "school_admin", "system_admin");
    private static final int BATCH_LIMIT = 500;

    private final HonorRepository honorRepository;
    private final HonorScreenLogRepository logRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public HonorScreenAdminService(HonorRepository honorRepository,
                                   HonorScreenLogRepository logRepository,
                                   JdbcTemplate jdbcTemplate,
                                   EntityManager entityManager) {
        this.honorRepository = honorRepository;
        this.logRepository = logRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------
    // 单条上下架
    // ------------------------------------------------------------------

    @Transactional
    public ScreenStatusResult setStatus(AuthPrincipal operator, Long honorId,
                                        SetScreenStatusRequest req, String ip, String requestId) {
        requireAdmin(operator);
        validateStatus(req == null ? null : req.status());
        if (honorId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "honor_id 不能为空");
        }
        Honor honor = honorRepository.findById(honorId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "荣誉不存在"));
        guardCollege(operator, honor);

        String before = honor.getScreenStatus();
        String after = req.status();
        if (before.equals(after)) {
            // 已是目标态：跳过，记录一条「noop」日志以便审计可追溯
            writeLog(honorId, HonorScreenLog.ACTION_ONLINE.equalsIgnoreCase(after)
                            ? HonorScreenLog.ACTION_BATCH_ONLINE : HonorScreenLog.ACTION_BATCH_OFFLINE,
                    before, after, req.reason(), operator, ip, requestId, 1);
            return new ScreenStatusResult(1, 0, 1, 0, List.of());
        }
        honor.setScreenStatus(after);
        honorRepository.save(honor);
        String action = "online".equals(after) ? HonorScreenLog.ACTION_ONLINE : HonorScreenLog.ACTION_OFFLINE;
        writeLog(honorId, action, before, after, req.reason(), operator, ip, requestId, 1);
        return new ScreenStatusResult(1, 1, 0, 0, List.of());
    }

    // ------------------------------------------------------------------
    // 批量上下架
    // ------------------------------------------------------------------

    @Transactional
    public ScreenStatusResult batchSet(AuthPrincipal operator,
                                       BatchScreenStatusRequest req, String ip, String requestId) {
        requireAdmin(operator);
        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "ids 不能为空");
        }
        if (req.ids().size() > BATCH_LIMIT) {
            throw new BizException(ErrorCode.BATCH_LIMIT_EXCEEDED,
                    "单次批量不超过 " + BATCH_LIMIT + " 条（当前 " + req.ids().size() + "）");
        }
        validateStatus(req.status());

        // 去重 + 按 id 排序，幂等
        List<Long> sortedIds = req.ids().stream().distinct().sorted().toList();

        // college_admin 一次性校验越权 id
        List<Honor> honors = honorRepository.findAllById(sortedIds);
        Map<Long, Honor> byId = new LinkedHashMap<>();
        for (Honor h : honors) byId.put(h.getId(), h);

        int applied = 0;
        int skipped = 0;
        List<ScreenStatusResult.FailedItem> failures = new ArrayList<>();
        String action = "online".equals(req.status()) ? HonorScreenLog.ACTION_BATCH_ONLINE
                : HonorScreenLog.ACTION_BATCH_OFFLINE;

        for (Long id : sortedIds) {
            Honor h = byId.get(id);
            if (h == null) {
                failures.add(new ScreenStatusResult.FailedItem(id, "荣誉不存在"));
                continue;
            }
            try {
                guardCollege(operator, h);
            } catch (BizException be) {
                failures.add(new ScreenStatusResult.FailedItem(id, be.getMessage()));
                continue;
            }
            if (req.status().equals(h.getScreenStatus())) {
                skipped++;
                continue;
            }
            String before = h.getScreenStatus();
            h.setScreenStatus(req.status());
            honorRepository.save(h);
            writeLog(h.getId(), action, before, req.status(), req.reason(), operator, ip, requestId,
                    sortedIds.size());
            applied++;
        }
        entityManager.flush();
        return new ScreenStatusResult(sortedIds.size(), applied, skipped, failures.size(), failures);
    }

    // ------------------------------------------------------------------
    // 日志分页
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ScreenLogPage listLogs(AuthPrincipal operator, Long honorId, String action,
                                  String operatorRole, int page, int pageSize) {
        requireAdmin(operator);
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 200) pageSize = 20;

        Specification<HonorScreenLog> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (honorId != null) {
                ps.add(cb.equal(root.get("honorId"), honorId));
            }
            if (action != null && !action.isBlank()) {
                ps.add(cb.equal(root.get("action"), action.trim()));
            }
            if (operatorRole != null && !operatorRole.isBlank()) {
                ps.add(cb.equal(root.get("operatorRole"), operatorRole.trim()));
            }
            if ("college_admin".equals(operator.role())) {
                // 院管只看本院日志（join honors + alumni_base.college）
                ps.add(cb.equal(root.get("operatorRole"), operator.role()));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<HonorScreenLog> p = logRepository.findAll(spec, pageable);
        List<ScreenLogItem> items = new ArrayList<>();
        // 一次批量查 honors 标题（避免 N+1）
        Map<Long, String> titleMap = new LinkedHashMap<>();
        if (!p.getContent().isEmpty()) {
            List<Long> ids = p.getContent().stream().map(HonorScreenLog::getHonorId).distinct().toList();
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, title FROM honors WHERE id IN (" + placeholders + ")", ids.toArray());
            for (Map<String, Object> r : rows) {
                titleMap.put(((Number) r.get("id")).longValue(), String.valueOf(r.get("title")));
            }
        }
        for (HonorScreenLog l : p.getContent()) {
            items.add(new ScreenLogItem(
                    l.getId(),
                    l.getHonorId(),
                    titleMap.get(l.getHonorId()),
                    l.getAction(),
                    l.getBeforeStatus(),
                    l.getAfterStatus(),
                    l.getReason(),
                    l.getOperatorId(),
                    l.getOperatorName(),
                    l.getOperatorRole(),
                    l.getClientIp(),
                    l.getBatchSize(),
                    l.getCreatedAt() == null ? null : l.getCreatedAt().toString()));
        }
        return new ScreenLogPage(items, p.getTotalElements(), page, pageSize);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void requireAdmin(AuthPrincipal op) {
        if (op == null || !ADMIN_ROLES.contains(op.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅院系/校级/系统管理员可操作上下架");
        }
    }

    private void validateStatus(String status) {
        if (status == null || status.isBlank() || !SCREEN_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "status 取值不合法（仅 online/offline）");
        }
    }

    private void guardCollege(AuthPrincipal op, Honor honor) {
        if (!"college_admin".equals(op.role())) {
            return;
        }
        String forced = resolveOperatorCollege(op.userId());
        String honorCol = resolveHonorCollege(honor.getUserId());
        if (forced == null || honorCol == null || !forced.equals(honorCol)) {
            throw new BizException(ErrorCode.FORBIDDEN, "荣誉不属于本院，禁止操作");
        }
    }

    private String resolveOperatorCollege(Long userId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT ab.college FROM users u JOIN alumni_base ab ON ab.id = u.alumni_base_id WHERE u.id = ?",
                String.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String resolveHonorCollege(Long userId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT ab.college FROM users u JOIN alumni_base ab ON ab.id = u.alumni_base_id WHERE u.id = ?",
                String.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void writeLog(Long honorId, String action, String before, String after, String reason,
                          AuthPrincipal op, String ip, String requestId, Integer batchSize) {
        String operatorName = resolveOperatorName(op.userId());
        jdbcTemplate.update("""
                INSERT INTO honor_screen_log
                  (honor_id, action, before_status, after_status, reason,
                   operator_id, operator_name, operator_role, client_ip, batch_size, request_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                honorId, action, before, after,
                reason == null ? null : (reason.length() > 500 ? reason.substring(0, 500) : reason),
                op.userId(), operatorName, op.role(),
                ip == null ? null : (ip.length() > 64 ? ip.substring(0, 64) : ip),
                batchSize, requestId,
                java.sql.Timestamp.from(Instant.now()));
    }

    private String resolveOperatorName(Long userId) {
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT ab.real_name FROM users u JOIN alumni_base ab ON ab.id = u.alumni_base_id WHERE u.id = ?",
                    String.class, userId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}