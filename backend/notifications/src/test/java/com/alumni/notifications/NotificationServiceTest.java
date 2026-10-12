package com.alumni.notifications;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.notifications.common.BizException;
import com.alumni.notifications.common.ErrorCode;
import com.alumni.notifications.dto.NotificationDtos.PageResult;
import com.alumni.notifications.dto.NotificationDtos.UnreadCount;
import com.alumni.notifications.entity.Notification;
import com.alumni.notifications.repository.NotificationRepository;
import com.alumni.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NotificationService 单元测试（Mockito mock Repository，无 HTTP / 无数据库）。
 *
 * <p>覆盖：列表过滤（无筛选 / type / unread / type+unread / 非法 type 400 / 分页参数收敛）、
 * 未读数聚合（by_type 仅含有未读的类型）、标记已读（正常 / 幂等 / 404 / 非本人 403 / 非法 id 400）、
 * 全部已读（全量 / 限定 type / 非法 type 400）、未登录 401。</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final AuthPrincipal ALUMNI = new AuthPrincipal(100L, "2021001", "alumni");

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository);
    }

    /** 无筛选：仅查本人消息，按 created_at 倒序（Repository 排序口径），字段映射完整 */
    @Test
    void page_noFilter_userScopedOrdered() {
        Notification newer = notification(2L, ALUMNI.userId(), "announcement", false,
                Instant.parse("2026-09-18T03:00:00Z"));
        Notification older = notification(1L, ALUMNI.userId(), "review", true,
                Instant.parse("2026-09-17T02:56:00Z"));
        when(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(ALUMNI.userId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newer, older), PageRequest.of(0, 20), 2));

        PageResult result = service.page(ALUMNI, null, false, 1, 20);

        assertEquals(2, result.total());
        assertEquals(1, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(2L, result.list().get(0).id(), "新的在前（created_at 倒序）");
        assertEquals("announcement", result.list().get(0).type());
        assertEquals(false, result.list().get(0).read());
        assertEquals(true, result.list().get(1).read());
        assertEquals(Instant.parse("2026-09-17T02:56:00Z"), result.list().get(1).createdAt());
    }

    /** type=review：带类型过滤 */
    @Test
    void page_typeFilter() {
        when(notificationRepository.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                eq(ALUMNI.userId()), eq("review"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResult result = service.page(ALUMNI, "review", false, 1, 20);

        assertEquals(0, result.total());
        verify(notificationRepository).findByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                eq(ALUMNI.userId()), eq("review"), any(Pageable.class));
        verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    /** unread=true：仅未读 */
    @Test
    void page_unreadOnly() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(
                eq(ALUMNI.userId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.page(ALUMNI, null, true, 1, 20);

        verify(notificationRepository).findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(
                eq(ALUMNI.userId()), any(Pageable.class));
    }

    /** type=todo&unread=true：类型 + 未读组合过滤 */
    @Test
    void page_typeAndUnread() {
        when(notificationRepository.findByUserIdAndTypeAndReadFalseOrderByCreatedAtDescIdDesc(
                eq(ALUMNI.userId()), eq("todo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.page(ALUMNI, "todo", true, 1, 20);

        verify(notificationRepository).findByUserIdAndTypeAndReadFalseOrderByCreatedAtDescIdDesc(
                eq(ALUMNI.userId()), eq("todo"), any(Pageable.class));
    }

    /** 非法 type → 400 INVALID_PARAMETER，且不触库 */
    @Test
    void page_invalidType_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.page(ALUMNI, "system", false, 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("type"));
        verifyNoInteractions(notificationRepository);
    }

    /** 分页参数收敛：page_size 上限 100，page 下限 1 */
    @Test
    void page_clampsPageParams() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(ALUMNI.userId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PageResult result = service.page(ALUMNI, "", false, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDescIdDesc(eq(ALUMNI.userId()), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber(), "page 下限收敛为 1");
        assertEquals(100, captor.getValue().getPageSize(), "page_size 上限收敛为 100");
        assertEquals(1, result.page());
        assertEquals(100, result.pageSize());
    }

    /** 未读数：total + by_type 聚合（by_type 仅含有未读的类型） */
    @Test
    void unreadCount_aggregatesByType() {
        when(notificationRepository.countByUserIdAndReadFalse(ALUMNI.userId())).thenReturn(4L);
        when(notificationRepository.countUnreadGroupByType(ALUMNI.userId()))
                .thenReturn(List.of(new Object[]{"review", 3L}, new Object[]{"announcement", 1L}));

        UnreadCount count = service.unreadCount(ALUMNI);

        assertEquals(4L, count.total());
        assertEquals(2, count.byType().size());
        assertEquals(3L, count.byType().get("review"));
        assertEquals(1L, count.byType().get("announcement"));
        assertFalse(count.byType().containsKey("todo"), "无未读的类型不出现在 by_type");
    }

    /** 标记已读：本人的未读消息 → 置为已读并保存 */
    @Test
    void markRead_happyPath() {
        Notification notification = notification(9L, ALUMNI.userId(), "review", false,
                Instant.parse("2026-09-17T02:56:00Z"));
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification));

        Map<String, Object> data = service.markRead(ALUMNI, 9L);

        assertEquals(9L, data.get("id"));
        assertEquals(true, data.get("read"));
        assertTrue(notification.getRead(), "实体已置为已读");
        verify(notificationRepository).save(notification);
    }

    /** 幂等：已是已读 → 直接返回，不再保存 */
    @Test
    void markRead_alreadyRead_idempotent() {
        Notification notification = notification(9L, ALUMNI.userId(), "review", true,
                Instant.parse("2026-09-17T02:56:00Z"));
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification));

        Map<String, Object> data = service.markRead(ALUMNI, 9L);

        assertEquals(true, data.get("read"));
        verify(notificationRepository, never()).save(any());
    }

    /** 消息不存在 → 404 */
    @Test
    void markRead_notFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.markRead(ALUMNI, 999L));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(notificationRepository, never()).save(any());
    }

    /** 越权红线：操作他人消息 → 403 FORBIDDEN，且不保存 */
    @Test
    void markRead_notOwner_forbidden() {
        Notification notification = notification(9L, 200L, "review", false,
                Instant.parse("2026-09-17T02:56:00Z")); // 归属 user 200
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification));

        BizException e = assertThrows(BizException.class, () -> service.markRead(ALUMNI, 9L));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verify(notificationRepository, never()).save(any());
    }

    /** id 非正整数 → 400 */
    @Test
    void markRead_invalidId_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.markRead(ALUMNI, 0L));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(notificationRepository);
    }

    /** 全部已读（不限 type）：返回更新数 */
    @Test
    void markAllRead_noType_returnsUpdated() {
        when(notificationRepository.markAllReadByUserId(ALUMNI.userId())).thenReturn(3);

        Map<String, Object> data = service.markAllRead(ALUMNI, null);

        assertEquals(3, data.get("updated"));
        verify(notificationRepository).markAllReadByUserId(ALUMNI.userId());
        verify(notificationRepository, never()).markAllReadByUserIdAndType(any(), any());
    }

    /** 全部已读（限定 type=review）：按类型更新 */
    @Test
    void markAllRead_withType() {
        when(notificationRepository.markAllReadByUserIdAndType(ALUMNI.userId(), "review")).thenReturn(2);

        Map<String, Object> data = service.markAllRead(ALUMNI, "review");

        assertEquals(2, data.get("updated"));
        verify(notificationRepository).markAllReadByUserIdAndType(ALUMNI.userId(), "review");
        verify(notificationRepository, never()).markAllReadByUserId(any());
    }

    /** 全部已读：非法 type → 400，且不触库 */
    @Test
    void markAllRead_invalidType_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.markAllRead(ALUMNI, "system"));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(notificationRepository);
    }

    /** 未登录 → 401 UNAUTHORIZED */
    @Test
    void page_unauthenticated_unauthorized() {
        BizException e = assertThrows(BizException.class, () -> service.page(null, null, false, 1, 20));
        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
        verifyNoInteractions(notificationRepository);
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private Notification notification(long id, long userId, String type, boolean read, Instant createdAt) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setType(type);
        n.setTitle("标题" + id);
        n.setContent("内容" + id);
        n.setBizType("honor");
        n.setBizId(12L);
        n.setRead(read);
        n.setCreatedAt(createdAt);
        return n;
    }
}
