package com.alumni.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审计日志模块出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class AuditLogDtos {

    private AuditLogDtos() {
    }

    /** 列表项（记录结构与《API 规范 P1》6.1 对齐） */
    public record AuditLogItem(Long id, Long userId, String userRole, String action,
                               String bizType, Long bizId, Map<String, Object> detail,
                               String ip, Instant createdAt) {
    }

    /** 分页包装（与 announcement 模块 PageResult 格式一致） */
    public record PageResult(List<AuditLogItem> list, long total, int page, int pageSize) {
    }

    /** 动作字典项（筛选下拉用） */
    public record ActionItem(String action, String label) {
    }
}
