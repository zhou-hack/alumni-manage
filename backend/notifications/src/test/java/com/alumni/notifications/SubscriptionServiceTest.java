package com.alumni.notifications;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.notifications.common.BizException;
import com.alumni.notifications.common.ErrorCode;
import com.alumni.notifications.dto.SubscriptionDtos.EmailConfig;
import com.alumni.notifications.dto.SubscriptionDtos.MyResponse;
import com.alumni.notifications.dto.SubscriptionDtos.SmsConfig;
import com.alumni.notifications.dto.SubscriptionDtos.UpdateRequest;
import com.alumni.notifications.entity.NotificationSubscription;
import com.alumni.notifications.repository.NotificationSubscriptionRepository;
import com.alumni.notifications.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SubscriptionService 单元测试（Mockito mock Repository，无 HTTP / 无数据库）。
 *
 * <p>覆盖：我的订阅（默认全关 / 已有记录原样返回）、更新订阅 upsert（新增两渠道 /
 * 覆盖已有记录 / 渠道归属当前用户）、参数校验（scenes 非法 400 / email 启用无地址 400 /
 * email 地址无 @ 400 / 缺渠道 400 / enabled 缺省按 false）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final AuthPrincipal ALUMNI = new AuthPrincipal(100L, "2021001", "alumni");

    @Mock
    private NotificationSubscriptionRepository subscriptionRepository;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository);
    }

    /** 我的订阅：无任何记录 → 默认全关（enabled=false, scenes=[]），email 地址为 null */
    @Test
    void my_noRecords_defaultsAllOff() {
        when(subscriptionRepository.findByUserId(ALUMNI.userId())).thenReturn(List.of());

        MyResponse data = service.my(ALUMNI);

        assertEquals(false, data.sms().enabled());
        assertEquals(List.of(), data.sms().scenes());
        assertEquals(false, data.email().enabled());
        assertNull(data.email().address());
        assertEquals(List.of(), data.email().scenes());
    }

    /** 我的订阅：已有记录按库存值返回 */
    @Test
    void my_existingRecords_returned() {
        NotificationSubscription sms = subscription(1L, ALUMNI.userId(), "sms", true,
                List.of("review_result"), null);
        NotificationSubscription email = subscription(2L, ALUMNI.userId(), "email", false,
                List.of(), "alumni@example.com");
        when(subscriptionRepository.findByUserId(ALUMNI.userId())).thenReturn(List.of(sms, email));

        MyResponse data = service.my(ALUMNI);

        assertEquals(true, data.sms().enabled());
        assertEquals(List.of("review_result"), data.sms().scenes());
        assertEquals(false, data.email().enabled());
        assertEquals("alumni@example.com", data.email().address());
        assertEquals(List.of(), data.email().scenes());
    }

    /** 更新订阅：无记录时 upsert 新增 sms / email 两条，归属当前用户，返回更新后配置 */
    @Test
    void update_upsert_createsBothChannels() {
        List<NotificationSubscription> store = new ArrayList<>();
        when(subscriptionRepository.findByUserIdAndChannel(anyLong(), anyString())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(NotificationSubscription.class))).thenAnswer(inv -> {
            NotificationSubscription s = inv.getArgument(0);
            store.add(s);
            return s;
        });
        when(subscriptionRepository.findByUserId(ALUMNI.userId())).thenAnswer(inv -> store);

        UpdateRequest request = new UpdateRequest(
                new SmsConfig(true, List.of("review_result", "announcement")),
                new EmailConfig(true, "alumni@example.com", List.of("announcement")));
        MyResponse data = service.update(ALUMNI, request);

        assertEquals(2, store.size());
        NotificationSubscription sms = store.get(0);
        assertEquals(ALUMNI.userId(), sms.getUserId(), "订阅归属当前用户");
        assertEquals("sms", sms.getChannel());
        assertTrue(sms.getEnabled());
        assertEquals(List.of("review_result", "announcement"), sms.getScenes());
        assertNull(sms.getAddress(), "sms 不存地址（沿用账号绑定手机号）");
        NotificationSubscription email = store.get(1);
        assertEquals("email", email.getChannel());
        assertTrue(email.getEnabled());
        assertEquals("alumni@example.com", email.getAddress());
        assertEquals(List.of("announcement"), email.getScenes());

        assertEquals(true, data.sms().enabled());
        assertEquals(true, data.email().enabled());
        assertEquals("alumni@example.com", data.email().address());
    }

    /** 更新订阅：已有记录被覆盖更新（uk(user_id,channel) 不新增行） */
    @Test
    void update_upsert_overwritesExisting() {
        NotificationSubscription existingSms = subscription(1L, ALUMNI.userId(), "sms", false,
                List.of(), null);
        NotificationSubscription existingEmail = subscription(2L, ALUMNI.userId(), "email", true,
                List.of("review_result"), "old@example.com");
        when(subscriptionRepository.findByUserIdAndChannel(ALUMNI.userId(), "sms"))
                .thenReturn(Optional.of(existingSms));
        when(subscriptionRepository.findByUserIdAndChannel(ALUMNI.userId(), "email"))
                .thenReturn(Optional.of(existingEmail));
        when(subscriptionRepository.save(any(NotificationSubscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.findByUserId(ALUMNI.userId()))
                .thenReturn(List.of(existingSms, existingEmail));

        UpdateRequest request = new UpdateRequest(
                new SmsConfig(true, List.of("review_result")),
                new EmailConfig(false, "old@example.com", List.of()));
        MyResponse data = service.update(ALUMNI, request);

        ArgumentCaptor<NotificationSubscription> captor = ArgumentCaptor.forClass(NotificationSubscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(s -> s.getId() != null), "更新已有行而非新增");
        assertTrue(existingSms.getEnabled());
        assertEquals(List.of("review_result"), existingSms.getScenes());
        assertFalse(existingEmail.getEnabled(), "退订即时生效");
        assertEquals(List.of(), existingEmail.getScenes());
        assertEquals(false, data.email().enabled());
    }

    /** scenes 含非法值 → 400 INVALID_PARAMETER，且不保存 */
    @Test
    void update_invalidScenes_badRequest() {
        UpdateRequest request = new UpdateRequest(
                new SmsConfig(true, List.of("review_result", "system_notice")),
                new EmailConfig(false, null, List.of()));

        BizException e = assertThrows(BizException.class, () -> service.update(ALUMNI, request));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("scenes"));
        verify(subscriptionRepository, never()).save(any());
    }

    /** email.enabled=true 且无地址 → 400 */
    @Test
    void update_emailEnabledWithoutAddress_badRequest() {
        when(subscriptionRepository.findByUserIdAndChannel(anyLong(), anyString())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(NotificationSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRequest request = new UpdateRequest(
                new SmsConfig(false, List.of()),
                new EmailConfig(true, null, List.of("review_result")));

        BizException e = assertThrows(BizException.class, () -> service.update(ALUMNI, request));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("address"));
    }

    /** email.enabled=true 且地址不含 @ → 400 */
    @Test
    void update_emailEnabledWithMalformedAddress_badRequest() {
        when(subscriptionRepository.findByUserIdAndChannel(anyLong(), anyString())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(NotificationSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRequest request = new UpdateRequest(
                new SmsConfig(false, List.of()),
                new EmailConfig(true, "not-an-email", List.of()));

        BizException e = assertThrows(BizException.class, () -> service.update(ALUMNI, request));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(e.getMessage().contains("email"));
    }

    /** 缺少 sms / email 任一渠道 → 400，且不触库 */
    @Test
    void update_missingChannel_badRequest() {
        UpdateRequest request = new UpdateRequest(null, new EmailConfig(false, null, List.of()));

        BizException e = assertThrows(BizException.class, () -> service.update(ALUMNI, request));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(subscriptionRepository);
    }

    /** enabled 缺省（null）按 false 处理（默认全关，最小打扰原则）；scenes 缺省存空数组 */
    @Test
    void update_nullEnabled_defaultsOff() {
        List<NotificationSubscription> store = new ArrayList<>();
        when(subscriptionRepository.findByUserIdAndChannel(anyLong(), anyString())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(NotificationSubscription.class))).thenAnswer(inv -> {
            NotificationSubscription s = inv.getArgument(0);
            store.add(s);
            return s;
        });
        when(subscriptionRepository.findByUserId(ALUMNI.userId())).thenAnswer(inv -> store);

        UpdateRequest request = new UpdateRequest(
                new SmsConfig(null, null),
                new EmailConfig(null, "alumni@example.com", null));
        MyResponse data = service.update(ALUMNI, request);

        assertFalse(store.get(0).getEnabled(), "enabled 缺省按 false");
        assertEquals(List.of(), store.get(0).getScenes(), "scenes 缺省存空数组");
        assertFalse(store.get(1).getEnabled(), "email 未启用时无地址也合法");
        assertEquals("alumni@example.com", store.get(1).getAddress(), "未启用也可保留地址");
        assertEquals(false, data.sms().enabled());
        assertEquals(false, data.email().enabled());
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private NotificationSubscription subscription(long id, long userId, String channel, boolean enabled,
                                                  List<String> scenes, String address) {
        NotificationSubscription s = new NotificationSubscription();
        s.setId(id);
        s.setUserId(userId);
        s.setChannel(channel);
        s.setEnabled(enabled);
        s.setScenes(scenes);
        s.setAddress(address);
        return s;
    }
}
