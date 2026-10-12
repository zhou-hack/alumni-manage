package com.alumni.notifications.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 站内消息出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** 消息列表项（read 对应 DB is_read；出参与《API 规范》示例一致） */
    public record NotificationItem(Long id, String type, String title, String content,
                                   String bizType, Long bizId, Boolean read, Instant createdAt) {
    }

    /** 分页包装（与其他模块 PageResult 格式一致） */
    public record PageResult(List<NotificationItem> list, long total, int page, int pageSize) {
    }

    /** 未读数（红点）：by_type 仅含有未读的类型 */
    public record UnreadCount(long total, Map<String, Long> byType) {
    }
}
