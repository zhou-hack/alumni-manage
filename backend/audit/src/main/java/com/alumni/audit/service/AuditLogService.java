package com.alumni.audit.service;

import com.alumni.audit.common.BizException;
import com.alumni.audit.common.ErrorCode;
import com.alumni.audit.dto.AuditLogDtos.ActionItem;
import com.alumni.audit.dto.AuditLogDtos.AuditLogItem;
import com.alumni.audit.dto.AuditLogDtos.PageResult;
import com.alumni.audit.entity.AuditLog;
import com.alumni.audit.repository.AuditLogRepository;
import com.alumni.common.security.AuthPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志业务实现（[M16]，Service 层）。
 *
 * <p>职责：system_admin 角色校验（SecurityConfig 之外的第二道防线）、
 * 筛选参数校验、动作字典、xlsx 导出与导出限流、导出行为自审计。</p>
 *
 * <p>简化决策：规范 6.1 描述导出为"异步任务"，本模块按同步流式导出实现
 * （POI 生成 xlsx 字节流随响应直接返回），数据量受 created_at 索引与筛选条件约束，
 * 后续如需支撑超大结果集再升级为异步任务 + 通知下载。</p>
 *
 * <p>只追加红线：本类仅调用 Repository 的查询与 save，不存在任何 UPDATE/DELETE 路径。</p>
 */
@Service
public class AuditLogService {

    private static final String ROLE_SYSTEM_ADMIN = "system_admin";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** P1 登记动作枚举 → 中文 label（/audit-logs/actions 字典，顺序即下拉展示顺序） */
    private static final List<ActionItem> ACTIONS = List.of(
            new ActionItem("login", "登录"),
            new ActionItem("login_failed", "登录失败"),
            new ActionItem("logout", "登出"),
            new ActionItem("export", "数据导出"),
            new ActionItem("attachment_delete", "附件删除"),
            new ActionItem("field_permission_change", "字段权限变更"),
            new ActionItem("user_suspend", "账号停用"),
            new ActionItem("user_resume", "账号恢复"),
            new ActionItem("user_handover", "账号交接"),
            new ActionItem("consent_grant", "隐私授权"),
            new ActionItem("consent_withdraw", "授权撤回"),
            new ActionItem("dict_change", "字典变更"),
            new ActionItem("tag_change", "标签变更"),
            new ActionItem("counselor_scope_change", "辅导员分管范围变更"),
            new ActionItem("todo_remind", "待办提醒"));

    private static final String[] EXPORT_HEADERS =
            {"id", "user_id", "user_role", "action", "biz_type", "biz_id", "detail", "ip", "created_at"};

    private final AuditLogRepository auditLogRepository;
    private final ExportRateLimiter exportRateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogService(AuditLogRepository auditLogRepository, ExportRateLimiter exportRateLimiter) {
        this.auditLogRepository = auditLogRepository;
        this.exportRateLimiter = exportRateLimiter;
    }

    /** 审计日志查询（仅 system_admin）：多条件组合筛选，created_at 倒序分页 */
    @Transactional(readOnly = true)
    public PageResult page(AuthPrincipal operator, Long userId, String action, String bizType,
                           String dateFrom, String dateTo, int page, int pageSize) {
        requireSystemAdmin(operator);
        DateRange range = parseDateRange(dateFrom, dateTo);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);

        Page<AuditLog> result = auditLogRepository.findAll(
                buildSpec(userId, action, bizType, range),
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PageResult(result.map(this::toItem).getContent(),
                result.getTotalElements(), safePage, safeSize);
    }

    /** 动作字典（仅 system_admin）：筛选下拉用，覆盖 P1 登记全部枚举 */
    public Map<String, Object> actions(AuthPrincipal operator) {
        requireSystemAdmin(operator);
        return Map.of("list", ACTIONS);
    }

    /**
     * 导出 xlsx（仅 system_admin）：单账号 5 次/小时滑动窗口限流；
     * 接受与列表相同的筛选参数；导出成功本身写审计日志（action=export, biz_type=audit_log）。
     */
    @Transactional
    public byte[] export(AuthPrincipal operator, Long userId, String action, String bizType,
                         String dateFrom, String dateTo, String ip) {
        requireSystemAdmin(operator);
        if (!exportRateLimiter.tryAcquire(operator.userId())) {
            throw new BizException(ErrorCode.EXPORT_LIMIT_EXCEEDED, "导出过于频繁：单账号每小时最多 5 次");
        }
        DateRange range = parseDateRange(dateFrom, dateTo);

        List<AuditLog> rows = auditLogRepository.findAll(
                buildSpec(userId, action, bizType, range),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        byte[] bytes = toXlsx(rows);
        writeExportAudit(operator, userId, action, bizType, dateFrom, dateTo, rows.size(), ip);
        return bytes;
    }

    /* ---------------- 私有工具 ---------------- */

    private void requireSystemAdmin(AuthPrincipal operator) {
        if (operator == null || !ROLE_SYSTEM_ADMIN.equals(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅系统管理员可访问审计日志");
        }
    }

    /** 闭区间日期 → created_at 半开区间 [from 00:00, to+1 天 00:00)（UTC 整天） */
    private DateRange parseDateRange(String dateFrom, String dateTo) {
        LocalDate from = parseDate(dateFrom, "date_from");
        LocalDate to = parseDate(dateTo, "date_to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "date_from 不能晚于 date_to");
        }
        return new DateRange(
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private LocalDate parseDate(String value, String param) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, param + " 格式须为 YYYY-MM-DD");
        }
    }

    private record DateRange(Instant fromInclusive, Instant toExclusive) {
    }

    private Specification<AuditLog> buildSpec(Long userId, String action, String bizType, DateRange range) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (bizType != null && !bizType.isBlank()) {
                predicates.add(cb.equal(root.get("bizType"), bizType));
            }
            if (range.fromInclusive() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.fromInclusive()));
            }
            if (range.toExclusive() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), range.toExclusive()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private byte[] toXlsx(List<AuditLog> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("audit_logs");
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(EXPORT_HEADERS[i]);
            }
            int rowIndex = 1;
            for (AuditLog log : rows) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                if (log.getId() != null) {
                    row.createCell(col).setCellValue(log.getId());
                }
                col++;
                if (log.getUserId() != null) {
                    row.createCell(col).setCellValue(log.getUserId());
                }
                col++;
                row.createCell(col++).setCellValue(nullToEmpty(log.getUserRole()));
                row.createCell(col++).setCellValue(nullToEmpty(log.getAction()));
                row.createCell(col++).setCellValue(nullToEmpty(log.getBizType()));
                if (log.getBizId() != null) {
                    row.createCell(col).setCellValue(log.getBizId());
                }
                col++;
                row.createCell(col++).setCellValue(detailToJson(log.getDetail()));
                row.createCell(col++).setCellValue(nullToEmpty(log.getIp()));
                row.createCell(col).setCellValue(log.getCreatedAt() == null ? "" : log.getCreatedAt().toString());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("生成导出文件失败", e);
        }
    }

    private String detailToJson(Map<String, Object> detail) {
        if (detail == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return String.valueOf(detail);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 导出行为自审计（只追加一条，随导出事务提交） */
    private void writeExportAudit(AuthPrincipal operator, Long userId, String action, String bizType,
                                  String dateFrom, String dateTo, int rows, String ip) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("user_id", userId);
        detail.put("action", action);
        detail.put("biz_type", bizType);
        detail.put("date_from", dateFrom);
        detail.put("date_to", dateTo);
        detail.put("rows", rows);

        AuditLog audit = new AuditLog();
        audit.setUserId(operator.userId());
        audit.setUserRole(operator.role());
        audit.setAction("export");
        audit.setBizType("audit_log");
        audit.setDetail(detail);
        audit.setIp(ip);
        auditLogRepository.save(audit);
    }

    private AuditLogItem toItem(AuditLog log) {
        return new AuditLogItem(log.getId(), log.getUserId(), log.getUserRole(), log.getAction(),
                log.getBizType(), log.getBizId(), log.getDetail(), log.getIp(), log.getCreatedAt());
    }
}
