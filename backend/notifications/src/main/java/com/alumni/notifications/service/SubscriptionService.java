package com.alumni.notifications.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.notifications.common.BizException;
import com.alumni.notifications.common.ErrorCode;
import com.alumni.notifications.dto.SubscriptionDtos.EmailConfig;
import com.alumni.notifications.dto.SubscriptionDtos.MyResponse;
import com.alumni.notifications.dto.SubscriptionDtos.SmsConfig;
import com.alumni.notifications.dto.SubscriptionDtos.UpdateRequest;
import com.alumni.notifications.entity.NotificationSubscription;
import com.alumni.notifications.repository.NotificationSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 外部消息订阅业务实现（Service 层，[M14]）。
 *
 * <p>职责：登录校验、scenes 取值校验、email 地址基本校验、(user_id, channel) upsert；
 * 默认全关（无记录时返回 enabled=false、scenes=[]）；订阅/退订即时生效（直写库即生效）。</p>
 */
@Service
public class SubscriptionService {

    /** 订阅场景取值：review_result（审核结果）/ announcement（公告） */
    private static final Set<String> SCENES = Set.of("review_result", "announcement");
    private static final String CHANNEL_SMS = "sms";
    private static final String CHANNEL_EMAIL = "email";

    private final NotificationSubscriptionRepository subscriptionRepository;

    public SubscriptionService(NotificationSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 我的订阅配置：无记录的渠道返回默认全关（enabled=false, scenes=[]） */
    @Transactional(readOnly = true)
    public MyResponse my(AuthPrincipal operator) {
        requireLogin(operator);
        Map<String, NotificationSubscription> byChannel = subscriptionRepository
                .findByUserId(operator.userId()).stream()
                .collect(Collectors.toMap(NotificationSubscription::getChannel, Function.identity()));
        return new MyResponse(toSms(byChannel.get(CHANNEL_SMS)), toEmail(byChannel.get(CHANNEL_EMAIL)));
    }

    /** 更新订阅：sms / email 两个渠道整体 upsert，即时生效 */
    @Transactional
    public MyResponse update(AuthPrincipal operator, UpdateRequest request) {
        requireLogin(operator);
        if (request == null || request.sms() == null || request.email() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "sms 与 email 配置均需提交");
        }
        Long userId = operator.userId();
        upsert(userId, CHANNEL_SMS, request.sms().enabled(), request.sms().scenes(), null);
        upsert(userId, CHANNEL_EMAIL, request.email().enabled(), request.email().scenes(), request.email().address());
        return my(operator);
    }

    /* ---------------- 私有工具 ---------------- */

    private void upsert(Long userId, String channel, Boolean enabled, List<String> scenes, String address) {
        boolean on = Boolean.TRUE.equals(enabled);
        List<String> normalizedScenes = normalizeScenes(scenes);
        String normalizedAddress = CHANNEL_EMAIL.equals(channel) ? normalizeAddress(address, on) : null;

        NotificationSubscription subscription = subscriptionRepository
                .findByUserIdAndChannel(userId, channel)
                .orElseGet(() -> {
                    NotificationSubscription created = new NotificationSubscription();
                    created.setUserId(userId);
                    created.setChannel(channel);
                    return created;
                });
        subscription.setEnabled(on);
        subscription.setScenes(normalizedScenes);
        subscription.setAddress(normalizedAddress);
        subscriptionRepository.save(subscription);
    }

    /** scenes：null → 空数组；逐值校验，非法 400；去重保序 */
    private List<String> normalizeScenes(List<String> scenes) {
        if (scenes == null) {
            return List.of();
        }
        for (String scene : scenes) {
            if (!SCENES.contains(scene)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "scenes 仅允许 review_result/announcement");
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(scenes));
    }

    /** email 地址：enabled=true 时必填且做基本格式校验（含 @）；空白按 null 处理 */
    private String normalizeAddress(String address, boolean enabled) {
        if (address == null || address.isBlank()) {
            if (enabled) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "email 启用时 address 必填");
            }
            return null;
        }
        String trimmed = address.trim();
        if (enabled && !trimmed.contains("@")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "email 地址格式不正确");
        }
        return trimmed;
    }

    private void requireLogin(AuthPrincipal operator) {
        if (operator == null || operator.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
    }

    private SmsConfig toSms(NotificationSubscription subscription) {
        if (subscription == null) {
            return new SmsConfig(false, List.of());
        }
        return new SmsConfig(Boolean.TRUE.equals(subscription.getEnabled()), scenesOrEmpty(subscription));
    }

    private EmailConfig toEmail(NotificationSubscription subscription) {
        if (subscription == null) {
            return new EmailConfig(false, null, List.of());
        }
        return new EmailConfig(Boolean.TRUE.equals(subscription.getEnabled()),
                subscription.getAddress(), scenesOrEmpty(subscription));
    }

    private List<String> scenesOrEmpty(NotificationSubscription subscription) {
        return subscription.getScenes() == null ? List.of() : subscription.getScenes();
    }
}
