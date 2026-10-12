package com.alumni.notifications.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.notifications.common.BizException;
import com.alumni.notifications.common.ErrorCode;
import com.alumni.notifications.dto.NotificationDtos.NotificationItem;
import com.alumni.notifications.dto.NotificationDtos.PageResult;
import com.alumni.notifications.dto.NotificationDtos.UnreadCount;
import com.alumni.notifications.entity.Notification;
import com.alumni.notifications.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 站内消息业务实现（Service 层，[M14]）。
 *
 * <p>职责：登录与归属校验（仅本人消息）、type 取值校验、分页/未读数/已读编排；
 * 不含 SQL 与 HTTP 细节，全部通过 Repository 取数。</p>
 */
@Service
public class NotificationService {

    /** 消息类型取值：review（审核结果）/ announcement（公告）/ todo（待办提醒） */
    private static final Set<String> TYPES = Set.of("review", "announcement", "todo");

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 我的消息列表（?type=&unread=true&page=&page_size=，created_at 倒序） */
    @Transactional(readOnly = true)
    public PageResult page(AuthPrincipal operator, String type, boolean unreadOnly, int page, int pageSize) {
        requireLogin(operator);
        String effectiveType = normalizeType(type);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        Long userId = operator.userId();
        Page<Notification> result;
        if (effectiveType != null && unreadOnly) {
            result = notificationRepository.findByUserIdAndTypeAndReadFalseOrderByCreatedAtDescIdDesc(
                    userId, effectiveType, pageable);
        } else if (effectiveType != null) {
            result = notificationRepository.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                    userId, effectiveType, pageable);
        } else if (unreadOnly) {
            result = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(userId, pageable);
        } else {
            result = notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        }
        return new PageResult(result.map(this::toItem).getContent(),
                result.getTotalElements(), safePage, safeSize);
    }

    /** 未读数（红点）：{total, by_type:{...}}，by_type 仅含有未读的类型 */
    @Transactional(readOnly = true)
    public UnreadCount unreadCount(AuthPrincipal operator) {
        requireLogin(operator);
        long total = notificationRepository.countByUserIdAndReadFalse(operator.userId());
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : notificationRepository.countUnreadGroupByType(operator.userId())) {
            byType.put((String) row[0], (Long) row[1]);
        }
        return new UnreadCount(total, byType);
    }

    /** 标记已读（仅本人；不存在 404，非本人 403；幂等） */
    @Transactional
    public Map<String, Object> markRead(AuthPrincipal operator, long id) {
        requireLogin(operator);
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "消息不存在"));
        if (!notification.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅本人可操作自己的消息");
        }
        if (!Boolean.TRUE.equals(notification.getRead())) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        return Map.of("id", id, "read", true);
    }

    /** 全部已读（可限定 type），返回 {updated: n} */
    @Transactional
    public Map<String, Object> markAllRead(AuthPrincipal operator, String type) {
        requireLogin(operator);
        String effectiveType = normalizeType(type);
        int updated = effectiveType == null
                ? notificationRepository.markAllReadByUserId(operator.userId())
                : notificationRepository.markAllReadByUserIdAndType(operator.userId(), effectiveType);
        return Map.of("updated", updated);
    }

    /* ---------------- 私有工具 ---------------- */

    private void requireLogin(AuthPrincipal operator) {
        if (operator == null || operator.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
    }

    /** null/空串 → null（不筛选）；否则校验取值，非法 400 */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if (!TYPES.contains(type)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "type 仅允许 review/announcement/todo");
        }
        return type;
    }

    private NotificationItem toItem(Notification n) {
        return new NotificationItem(n.getId(), n.getType(), n.getTitle(), n.getContent(),
                n.getBizType(), n.getBizId(), n.getRead(), n.getCreatedAt());
    }
}
